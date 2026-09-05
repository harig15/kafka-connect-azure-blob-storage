package contracts.policies

import org.springframework.cloud.contract.spec.Contract

/**
 * The dashboard renders one card per policy on load, so the catalogue must expose disabled
 * policies too. The empty-conditions shape is pinned by shouldReturnAPolicyWithoutConditions
 * instead: Spring Cloud Contract collapses nested arrays to one $[*].['conditions'][*] path,
 * so a list mixing a policy with conditions and one without cannot be asserted here.
 */
Contract.make {
    description "should return the full policy catalogue, including disabled policies"

    request {
        method GET()
        url "/api/policies"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                [
                        id              : "policy-orders-cleanup",
                        name            : "Old Completed Orders Cleanup",
                        description     : "Removes completed purchase orders that are older than 30 days.",
                        databaseType    : "MONGODB",
                        collectionName  : "orders",
                        maxDeletionLimit: 1000,
                        agePeriodDays   : 30,
                        targetField     : "createdAt",
                        conditions      : [
                                [field: "status", operator: "EQ", value: "COMPLETED"]
                        ],
                        enabled         : true
                ],
                [
                        id              : "policy-audit-logs",
                        name            : "Old Audit Logs Cleanup",
                        description     : "Removes audit log entries older than 180 days.",
                        databaseType    : "MONGODB",
                        collectionName  : "audit_logs",
                        maxDeletionLimit: 2000,
                        agePeriodDays   : 180,
                        targetField     : "timestamp",
                        conditions      : [
                                [field: "archived", operator: "EQ", value: true]
                        ],
                        enabled         : false
                ]
        ])
    }
}
