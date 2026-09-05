package contracts.policies

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should answer 404 and start no execution when the policy id is unknown"

    request {
        method POST()
        url "/api/policies/policy-does-not-exist/execute"
    }

    response {
        status NOT_FOUND()
    }
}
