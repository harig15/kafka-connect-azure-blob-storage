package com.dbcleanup.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a data cleanup policy configuration.
 * Each policy defines the target collection, age threshold, deletion limits,
 * and any additional filter conditions for selecting documents to delete.
 */
public class CleanupPolicy {

    /** Unique identifier for the policy. */
    private String id;

    /** Human-readable policy name. */
    private String name;

    /** Description of what this policy cleans up. */
    private String description;

    /** Database type (currently only MONGODB is supported). */
    private String databaseType;

    /** Name of the MongoDB collection to clean up. */
    private String collectionName;

    /**
     * Maximum number of documents to delete in a single execution.
     * Acts as a safety limit to prevent accidental bulk deletions.
     */
    private int maxDeletionLimit;

    /**
     * Age threshold in days. Documents older than this value
     * (based on the targetField) will be eligible for deletion.
     */
    private int agePeriodDays;

    /**
     * The MongoDB document field containing the date/timestamp
     * used to determine document age.
     */
    private String targetField;

    /** Additional filter conditions applied alongside the age filter. */
    private List<PolicyCondition> conditions = new ArrayList<>();

    /** Whether this policy is active and available for execution. */
    private boolean enabled;

    public CleanupPolicy() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDatabaseType() { return databaseType; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public int getMaxDeletionLimit() { return maxDeletionLimit; }
    public void setMaxDeletionLimit(int maxDeletionLimit) { this.maxDeletionLimit = maxDeletionLimit; }

    public int getAgePeriodDays() { return agePeriodDays; }
    public void setAgePeriodDays(int agePeriodDays) { this.agePeriodDays = agePeriodDays; }

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }

    public List<PolicyCondition> getConditions() { return conditions; }
    public void setConditions(List<PolicyCondition> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public String toString() {
        return "CleanupPolicy{id='" + id + "', name='" + name + "', collection='" + collectionName + "'}";
    }
}
