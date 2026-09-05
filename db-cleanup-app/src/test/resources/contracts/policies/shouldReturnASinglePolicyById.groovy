package contracts.policies

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return a single policy when the id is known"

    request {
        method GET()
        url "/api/policies/policy-orders-cleanup"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
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
        ])
    }
}
