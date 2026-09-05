package contracts.executions

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return a single execution record when the id is known"

    request {
        method GET()
        url "/api/executions/exec-20260905-0001"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                id              : "exec-20260905-0001",
                policyId        : "policy-orders-cleanup",
                policyName      : "Old Completed Orders Cleanup",
                collectionName  : "orders",
                status          : "SUCCESS",
                documentsDeleted: 30,
                documentsMatched: 30,
                startTime       : "2026-09-05T10:15:30Z",
                endTime         : "2026-09-05T10:15:32Z",
                errorMessage    : null,
                durationMs      : 2000
        ])
    }
}
