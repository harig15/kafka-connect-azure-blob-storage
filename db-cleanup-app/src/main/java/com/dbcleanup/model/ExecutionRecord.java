package com.dbcleanup.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persisted record of a policy execution, stored in the cleanup_executions collection.
 * Each time a cleanup policy is triggered, a new ExecutionRecord is created and
 * updated as the deletion progresses.
 */
@Document(collection = "cleanup_executions")
public class ExecutionRecord {

    @Id
    private String id;

    private String policyId;
    private String policyName;
    private String collectionName;

    /**
     * Execution status: RUNNING, SUCCESS, FAILED, SKIPPED
     * SKIPPED is used when the policy is disabled or no documents match.
     */
    private String status;

    /** Number of documents successfully deleted. */
    private long documentsDeleted;

    /** Number of documents that matched the filter before applying the deletion limit. */
    private long documentsMatched;

    private Instant startTime;
    private Instant endTime;

    /** Populated when status is FAILED. */
    private String errorMessage;

    public ExecutionRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getDocumentsDeleted() { return documentsDeleted; }
    public void setDocumentsDeleted(long documentsDeleted) { this.documentsDeleted = documentsDeleted; }

    public long getDocumentsMatched() { return documentsMatched; }
    public void setDocumentsMatched(long documentsMatched) { this.documentsMatched = documentsMatched; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /** Returns the duration in milliseconds, or -1 if not yet complete. */
    public long getDurationMs() {
        if (startTime == null || endTime == null) return -1;
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
}
