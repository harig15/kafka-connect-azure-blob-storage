package com.dbcleanup.config;

import com.dbcleanup.model.CleanupPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads cleanup policy definitions from application configuration (policies.yml).
 * Policies are defined under the "cleanup.policies" key.
 *
 * Example YAML structure:
 * <pre>
 * cleanup:
 *   policies:
 *     - id: policy-001
 *       name: "Old Orders Cleanup"
 *       collectionName: orders
 *       agePeriodDays: 30
 *       maxDeletionLimit: 1000
 *       targetField: createdAt
 *       enabled: true
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "cleanup")
public class PolicyConfig {

    private List<CleanupPolicy> policies = new ArrayList<>();

    public List<CleanupPolicy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<CleanupPolicy> policies) {
        this.policies = policies != null ? policies : new ArrayList<>();
    }

    /**
     * Returns all enabled policies.
     */
    public List<CleanupPolicy> getEnabledPolicies() {
        return policies.stream().filter(CleanupPolicy::isEnabled).toList();
    }

    /**
     * Finds a policy by its ID.
     */
    public Optional<CleanupPolicy> findById(String id) {
        return policies.stream().filter(p -> p.getId().equals(id)).findFirst();
    }
}
