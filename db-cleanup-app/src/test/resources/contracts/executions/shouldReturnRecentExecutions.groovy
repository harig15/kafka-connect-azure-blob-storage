package contracts.executions

import org.springframework.cloud.contract.spec.Contract

/**
 * Backs the dashboard history table, which polls this endpoint. Every field the table
 * renders is pinned here, including the derived durationMs the record exposes.
 */
Contract.make {
    description "should return the most recent execution records, newest first"

    request {
        method GET()
        url "/api/executions"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                [
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
                ]
        ])
    }
}
