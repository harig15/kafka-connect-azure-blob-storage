package com.dbcleanup.contract;

import com.dbcleanup.config.PolicyConfig;
import com.dbcleanup.model.CleanupPolicy;
import com.dbcleanup.model.ExecutionRecord;
import com.dbcleanup.model.PolicyCondition;
import com.dbcleanup.repository.ExecutionRecordRepository;
import com.dbcleanup.service.CleanupService;
import com.dbcleanup.web.CleanupController;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Base class for every generated Spring Cloud Contract test.
 *
 * <p>The contracts under {@code src/test/resources/contracts} are compiled by the
 * spring-cloud-contract-maven-plugin into JUnit 5 tests that extend this class and
 * drive the <em>real</em> {@link CleanupController} through MockMvc. Only the layer
 * below the controller is faked, so the contract verifies the actual request
 * mapping, status codes, and Jackson serialisation that consumers depend on.
 *
 * <p>{@code @WebMvcTest} is deliberate: it loads Spring Boot's real Jackson
 * auto-configuration (so {@link Instant} fields serialise exactly as they do in
 * production) while leaving MongoDB, the embedded Mongo server, and the data
 * seeder out of the context. Contract tests therefore need no database and run in
 * about a second.
 *
 * <p>The fixtures below mirror {@code policies.yml} so the contracts double as
 * documentation of a realistic payload.
 */
@WebMvcTest(controllers = CleanupController.class)
public abstract class CleanupApiBase {

    /** Policy id that exists in every contract scenario. */
    protected static final String KNOWN_POLICY_ID = "policy-orders-cleanup";

    /** Policy id that is guaranteed to be absent, used by the 404 contracts. */
    protected static final String UNKNOWN_POLICY_ID = "policy-does-not-exist";

    /** Policy id whose fixture carries no conditions, pinning the empty-array shape. */
    protected static final String CONDITIONLESS_POLICY_ID = "policy-expired-sessions";

    /** Execution id that exists in every contract scenario. */
    protected static final String KNOWN_EXECUTION_ID = "exec-20260905-0001";

    /** Execution id that is guaranteed to be absent, used by the 404 contract. */
    protected static final String UNKNOWN_EXECUTION_ID = "exec-does-not-exist";

    /** Fixed clock reading so contract bodies can assert exact timestamps. */
    protected static final Instant STARTED_AT = Instant.parse("2026-09-05T10:15:30Z");

    /** Two seconds after {@link #STARTED_AT}, giving a durationMs of 2000. */
    protected static final Instant FINISHED_AT = STARTED_AT.plusSeconds(2);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private PolicyConfig policyConfig;

    @MockBean
    private CleanupService cleanupService;

    @MockBean
    private ExecutionRecordRepository executionRecordRepository;

    @BeforeEach
    void setUpContractFixtures() {
        RestAssuredMockMvc.webAppContextSetup(webApplicationContext);

        CleanupPolicy ordersPolicy = ordersPolicy();
        CleanupPolicy auditLogsPolicy = auditLogsPolicy();

        given(policyConfig.getPolicies()).willReturn(List.of(ordersPolicy, auditLogsPolicy));
        given(policyConfig.getEnabledPolicies()).willReturn(List.of(ordersPolicy));
        given(policyConfig.findById(anyString())).willReturn(Optional.empty());
        given(policyConfig.findById(KNOWN_POLICY_ID)).willReturn(Optional.of(ordersPolicy));
        given(policyConfig.findById(CONDITIONLESS_POLICY_ID))
                .willReturn(Optional.of(expiredSessionsPolicy()));

        given(cleanupService.createRunningRecord(KNOWN_POLICY_ID)).willReturn(runningExecution());

        given(executionRecordRepository.findTop50ByOrderByStartTimeDesc())
                .willReturn(List.of(completedExecution()));
        given(executionRecordRepository.findById(anyString())).willReturn(Optional.empty());
        given(executionRecordRepository.findById(KNOWN_EXECUTION_ID))
                .willReturn(Optional.of(completedExecution()));
    }

    /** Enabled policy carrying one additional condition. */
    private CleanupPolicy ordersPolicy() {
        CleanupPolicy policy = new CleanupPolicy();
        policy.setId(KNOWN_POLICY_ID);
        policy.setName("Old Completed Orders Cleanup");
        policy.setDescription("Removes completed purchase orders that are older than 30 days.");
        policy.setDatabaseType("MONGODB");
        policy.setCollectionName("orders");
        policy.setMaxDeletionLimit(1000);
        policy.setAgePeriodDays(30);
        policy.setTargetField("createdAt");
        policy.setEnabled(true);
        policy.setConditions(List.of(new PolicyCondition("status", "EQ", "COMPLETED")));
        return policy;
    }

    /**
     * Disabled policy, so the catalogue contract proves disabled policies are still listed.
     *
     * <p>It carries a condition on purpose. Spring Cloud Contract collapses assertions on a
     * nested array to a single {@code $[*].['conditions'][*]} path, so a catalogue holding one
     * policy with conditions and one without cannot be expressed. The empty-conditions shape is
     * pinned separately by {@link #expiredSessionsPolicy()}, which is served from the
     * single-policy endpoint where no such collapsing happens.
     */
    private CleanupPolicy auditLogsPolicy() {
        CleanupPolicy policy = new CleanupPolicy();
        policy.setId("policy-audit-logs");
        policy.setName("Old Audit Logs Cleanup");
        policy.setDescription("Removes audit log entries older than 180 days.");
        policy.setDatabaseType("MONGODB");
        policy.setCollectionName("audit_logs");
        policy.setMaxDeletionLimit(2000);
        policy.setAgePeriodDays(180);
        policy.setTargetField("timestamp");
        policy.setEnabled(false);
        policy.setConditions(List.of(new PolicyCondition("archived", "EQ", true)));
        return policy;
    }

    /**
     * Policy with no extra conditions, matching the real expired-sessions policy in
     * policies.yml. Pins the contract that {@code conditions} is an empty array and never null.
     */
    private CleanupPolicy expiredSessionsPolicy() {
        CleanupPolicy policy = new CleanupPolicy();
        policy.setId(CONDITIONLESS_POLICY_ID);
        policy.setName("Expired Sessions Purge");
        policy.setDescription("Purges session records that expired more than 7 days ago.");
        policy.setDatabaseType("MONGODB");
        policy.setCollectionName("sessions");
        policy.setMaxDeletionLimit(5000);
        policy.setAgePeriodDays(7);
        policy.setTargetField("expiresAt");
        policy.setEnabled(true);
        policy.setConditions(List.of());
        return policy;
    }

    /** Freshly created record as returned by POST /api/policies/{id}/execute. */
    private ExecutionRecord runningExecution() {
        ExecutionRecord record = new ExecutionRecord();
        record.setId("exec-20260905-0002");
        record.setPolicyId(KNOWN_POLICY_ID);
        record.setPolicyName("Old Completed Orders Cleanup");
        record.setCollectionName("orders");
        record.setStatus("RUNNING");
        record.setDocumentsDeleted(0);
        record.setDocumentsMatched(0);
        record.setStartTime(STARTED_AT);
        return record;
    }

    /** Finished record as returned by the execution history endpoints. */
    private ExecutionRecord completedExecution() {
        ExecutionRecord record = new ExecutionRecord();
        record.setId(KNOWN_EXECUTION_ID);
        record.setPolicyId(KNOWN_POLICY_ID);
        record.setPolicyName("Old Completed Orders Cleanup");
        record.setCollectionName("orders");
        record.setStatus("SUCCESS");
        record.setDocumentsDeleted(30);
        record.setDocumentsMatched(30);
        record.setStartTime(STARTED_AT);
        record.setEndTime(FINISHED_AT);
        return record;
    }
}
