package com.dbcleanup.service;

import com.dbcleanup.config.PolicyConfig;
import com.dbcleanup.model.CleanupPolicy;
import com.dbcleanup.model.ExecutionRecord;
import com.dbcleanup.model.PolicyCondition;
import com.dbcleanup.repository.ExecutionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Core service responsible for executing cleanup policies against MongoDB.
 *
 * For each execution:
 * 1. Builds a query using the policy's age filter + additional conditions
 * 2. Counts matching documents
 * 3. Deletes up to maxDeletionLimit documents
 * 4. Persists the ExecutionRecord with the result
 */
@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final PolicyConfig policyConfig;
    private final MongoTemplate mongoTemplate;
    private final ExecutionRecordRepository executionRecordRepository;

    public CleanupService(PolicyConfig policyConfig,
                          MongoTemplate mongoTemplate,
                          ExecutionRecordRepository executionRecordRepository) {
        this.policyConfig = policyConfig;
        this.mongoTemplate = mongoTemplate;
        this.executionRecordRepository = executionRecordRepository;
    }

    /**
     * Executes the given policy asynchronously.
     * Creates a RUNNING execution record immediately, then updates it upon completion.
     *
     * @param policyId the ID of the policy to execute
     * @return the initial ExecutionRecord (status=RUNNING); check the DB for final status
     */
    @Async
    public void executePolicy(String policyId, String executionId) {
        CleanupPolicy policy = policyConfig.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        ExecutionRecord record = executionRecordRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution record not found: " + executionId));

        log.info("Starting execution of policy '{}' (id={}) on collection '{}'",
                policy.getName(), policyId, policy.getCollectionName());

        try {
            if (!policy.isEnabled()) {
                record.setStatus("SKIPPED");
                record.setErrorMessage("Policy is disabled");
                record.setEndTime(Instant.now());
                executionRecordRepository.save(record);
                return;
            }

            Query query = buildQuery(policy);

            // Count matching documents before applying the limit
            long matched = mongoTemplate.count(query, policy.getCollectionName());
            record.setDocumentsMatched(matched);
            log.info("Policy '{}': {} documents match the filter", policy.getName(), matched);

            if (matched == 0) {
                record.setStatus("SUCCESS");
                record.setDocumentsDeleted(0);
                record.setDocumentsMatched(0);
                record.setEndTime(Instant.now());
                executionRecordRepository.save(record);
                log.info("Policy '{}': no documents to delete", policy.getName());
                return;
            }

            // Apply deletion limit
            Query limitedQuery = buildQuery(policy);
            limitedQuery.limit(policy.getMaxDeletionLimit());

            // Fetch IDs of documents to delete (to enforce limit safely)
            List<org.bson.Document> docsToDelete = mongoTemplate.find(
                    limitedQuery.fields().include("_id"),
                    org.bson.Document.class,
                    policy.getCollectionName()
            );

            long deleted = 0;
            for (org.bson.Document doc : docsToDelete) {
                Query deleteQuery = new Query(Criteria.where("_id").is(doc.get("_id")));
                mongoTemplate.remove(deleteQuery, policy.getCollectionName());
                deleted++;
            }

            record.setDocumentsDeleted(deleted);
            record.setDocumentsMatched(matched);
            record.setStatus("SUCCESS");
            record.setEndTime(Instant.now());
            executionRecordRepository.save(record);

            log.info("Policy '{}': successfully deleted {} documents (matched={}, limit={})",
                    policy.getName(), deleted, matched, policy.getMaxDeletionLimit());

        } catch (Exception e) {
            log.error("Policy '{}' execution failed: {}", policy.getName(), e.getMessage(), e);
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            record.setEndTime(Instant.now());
            executionRecordRepository.save(record);
        }
    }

    /**
     * Creates and persists an initial RUNNING execution record for the given policy.
     */
    public ExecutionRecord createRunningRecord(String policyId) {
        CleanupPolicy policy = policyConfig.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        ExecutionRecord record = new ExecutionRecord();
        record.setPolicyId(policyId);
        record.setPolicyName(policy.getName());
        record.setCollectionName(policy.getCollectionName());
        record.setStatus("RUNNING");
        record.setStartTime(Instant.now());
        record.setDocumentsDeleted(0);
        record.setDocumentsMatched(0);

        return executionRecordRepository.save(record);
    }

    /**
     * Builds a MongoDB Query from the policy's age filter and additional conditions.
     *
     * Age filter: targetField < (now - agePeriodDays)
     * Additional conditions are ANDed together.
     */
    private Query buildQuery(CleanupPolicy policy) {
        Instant cutoff = Instant.now().minus(policy.getAgePeriodDays(), ChronoUnit.DAYS);
        Criteria ageCriteria = Criteria.where(policy.getTargetField()).lt(Date.from(cutoff));

        Criteria finalCriteria = ageCriteria;

        if (policy.getConditions() != null && !policy.getConditions().isEmpty()) {
            Criteria[] additionalCriteria = policy.getConditions().stream()
                    .map(this::buildConditionCriteria)
                    .toArray(Criteria[]::new);
            finalCriteria = new Criteria().andOperator(
                    ageCriteria,
                    new Criteria().andOperator(additionalCriteria)
            );
        }

        return new Query(finalCriteria);
    }

    /**
     * Translates a PolicyCondition into a Spring Data MongoDB Criteria.
     */
    private Criteria buildConditionCriteria(PolicyCondition condition) {
        Criteria criteria = Criteria.where(condition.getField());
        return switch (condition.getOperator().toUpperCase()) {
            case "EQ"     -> criteria.is(condition.getValue());
            case "NEQ"    -> criteria.ne(condition.getValue());
            case "LT"     -> criteria.lt(condition.getValue());
            case "LTE"    -> criteria.lte(condition.getValue());
            case "GT"     -> criteria.gt(condition.getValue());
            case "GTE"    -> criteria.gte(condition.getValue());
            case "EXISTS" -> criteria.exists((Boolean) condition.getValue());
            default -> throw new IllegalArgumentException(
                    "Unsupported operator: " + condition.getOperator());
        };
    }
}
