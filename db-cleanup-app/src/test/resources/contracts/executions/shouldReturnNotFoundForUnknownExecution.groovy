package contracts.executions

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should answer 404 when the execution id is unknown"

    request {
        method GET()
        url "/api/executions/exec-does-not-exist"
    }

    response {
        status NOT_FOUND()
    }
}
