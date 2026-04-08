package com.dbcleanup.web;

import com.dbcleanup.config.PolicyConfig;
import com.dbcleanup.model.CleanupPolicy;
import com.dbcleanup.model.ExecutionRecord;
import com.dbcleanup.repository.ExecutionRecordRepository;
import com.dbcleanup.service.CleanupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing and executing cleanup policies.
 *
 * Endpoints:
 *   GET  /api/policies              - list all configured policies
 *   GET  /api/policies/{id}         - get a single policy by ID
 *   POST /api/policies/{id}/execute - trigger on-demand execution of a policy
 *   GET  /api/executions            - list the 50 most recent execution records
 *   GET  /api/executions/{id}       - get a specific execution record
 *   GET  /api/stats                 - summary statistics for the dashboard
 */
@RestController
@RequestMapping("/api")
public class CleanupController {

    private final PolicyConfig policyConfig;
    private final CleanupService cleanupService;
    private final ExecutionRecordRepository executionRecordRepository;

    public CleanupController(PolicyConfig policyConfig,
                             CleanupService cleanupService,
                             ExecutionRecordRepository executionRecordRepository) {
        this.policyConfig = policyConfig;
        this.cleanupService = cleanupService;
        this.executionRecordRepository = executionRecordRepository;
    }

    // -------------------------------------------------------------------------
    // Policy endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/policies")
    public List<CleanupPolicy> getAllPolicies() {
        return policyConfig.getPolicies();
    }

    @GetMapping("/policies/{id}")
    public ResponseEntity<CleanupPolicy> getPolicy(@PathVariable String id) {
        return policyConfig.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Triggers on-demand execution of a policy.
     * Returns the initial execution record immediately (status=RUNNING);
     * the deletion runs asynchronously.
     */
    @PostMapping("/policies/{id}/execute")
    public ResponseEntity<?> executePolicy(@PathVariable String id) {
        if (policyConfig.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Persist a RUNNING record first so the dashboard can show progress
        ExecutionRecord record = cleanupService.createRunningRecord(id);

        // Execute asynchronously
        cleanupService.executePolicy(id, record.getId());

        return ResponseEntity.accepted().body(record);
    }

    // -------------------------------------------------------------------------
    // Execution history endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/executions")
    public List<ExecutionRecord> getExecutions() {
        return executionRecordRepository.findTop50ByOrderByStartTimeDesc();
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<ExecutionRecord> getExecution(@PathVariable String id) {
        return executionRecordRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // Statistics endpoint
    // -------------------------------------------------------------------------

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<ExecutionRecord> all = executionRecordRepository.findTop50ByOrderByStartTimeDesc();

        long totalDeleted = all.stream()
                .filter(r -> "SUCCESS".equals(r.getStatus()))
                .mapToLong(ExecutionRecord::getDocumentsDeleted)
                .sum();

        long successCount = all.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        long failedCount  = all.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long runningCount = all.stream().filter(r -> "RUNNING".equals(r.getStatus())).count();

        return Map.of(
                "totalPolicies",   policyConfig.getPolicies().size(),
                "enabledPolicies", policyConfig.getEnabledPolicies().size(),
                "totalExecutions", all.size(),
                "successCount",    successCount,
                "failedCount",     failedCount,
                "runningCount",    runningCount,
                "totalDocumentsDeleted", totalDeleted
        );
    }
}
