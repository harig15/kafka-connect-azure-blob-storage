package contracts.policies

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should answer 404 rather than an error payload when the policy id is unknown"

    request {
        method GET()
        url "/api/policies/policy-does-not-exist"
    }

    response {
        status NOT_FOUND()
    }
}
