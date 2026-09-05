package contracts.policies

import org.springframework.cloud.contract.spec.Contract

/**
 * A policy that filters on age alone still exposes conditions as an empty array, never null
 * and never absent. The dashboard iterates that array without a guard, so the shape matters.
 */
Contract.make {
    description "should expose an empty conditions array for a policy that has no extra filters"

    request {
        method GET()
        url "/api/policies/policy-expired-sessions"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                id              : "policy-expired-sessions",
                name            : "Expired Sessions Purge",
                description     : "Purges session records that expired more than 7 days ago.",
                databaseType    : "MONGODB",
                collectionName  : "sessions",
                maxDeletionLimit: 5000,
                agePeriodDays   : 7,
                targetField     : "expiresAt",
                conditions      : [],
                enabled         : true
        ])
    }
}
