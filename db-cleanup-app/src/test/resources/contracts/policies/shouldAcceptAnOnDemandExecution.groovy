package contracts.policies

import org.springframework.cloud.contract.spec.Contract

/**
 * Execution is asynchronous. The caller gets 202 Accepted plus the RUNNING record
 * straight away, and polls the execution endpoints for the outcome. A consumer that
 * expects 200 with a finished record would be broken by this API, so the status code
 * and the RUNNING placeholder values are both part of the contract.
 */
Contract.make {
    description "should accept an execution request and return the RUNNING record immediately"

    request {
        method POST()
        url "/api/policies/policy-orders-cleanup/execute"
    }

    response {
        status ACCEPTED()
        headers {
            contentType applicationJson()
        }
        body([
                id              : "exec-20260905-0002",
                policyId        : "policy-orders-cleanup",
                policyName      : "Old Completed Orders Cleanup",
                collectionName  : "orders",
                status          : "RUNNING",
                documentsDeleted: 0,
                documentsMatched: 0,
                startTime       : "2026-09-05T10:15:30Z",
                endTime         : null,
                errorMessage    : null,
                durationMs      : -1
        ])
    }
}
