package com.dbcleanup.repository;

import com.dbcleanup.model.ExecutionRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for storing and querying cleanup execution records.
 */
@Repository
public interface ExecutionRecordRepository extends MongoRepository<ExecutionRecord, String> {

    /**
     * Returns all execution records for a given policy, ordered by most recent first.
     */
    List<ExecutionRecord> findByPolicyIdOrderByStartTimeDesc(String policyId);

    /**
     * Returns the most recent N execution records across all policies.
     */
    List<ExecutionRecord> findTop50ByOrderByStartTimeDesc();

    /**
     * Returns all executions currently in RUNNING state (for dashboard refresh logic).
     */
    List<ExecutionRecord> findByStatus(String status);
}
