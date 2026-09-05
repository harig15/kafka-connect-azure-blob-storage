package contracts.stats

import org.springframework.cloud.contract.spec.Contract

/**
 * Feeds the four stat cards at the top of the dashboard. The key names are the contract:
 * the browser reads them directly, so a rename here breaks the UI silently.
 */
Contract.make {
    description "should return the summary counters used by the dashboard stat cards"

    request {
        method GET()
        url "/api/stats"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                totalPolicies        : 2,
                enabledPolicies      : 1,
                totalExecutions      : 1,
                successCount         : 1,
                failedCount          : 0,
                runningCount         : 0,
                totalDocumentsDeleted: 30
        ])
    }
}
