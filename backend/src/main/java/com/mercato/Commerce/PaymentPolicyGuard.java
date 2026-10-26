package com.mercato.Commerce;

import org.springframework.beans.factory.annotation.Value;

/** 4A deployment boundary. 4C must replace this only after proof and unit/settlement integration. */
public class PaymentPolicyGuard {
    public PaymentPolicyGuard(
            @Value("${mercato.payments.default-policy:CAPTURE_ON_AUTH}") PaymentPolicy requestedPolicy,
            @Value("${mercato.payments.authorization-enabled:false}") boolean authorizationEnabled) {
        if (requestedPolicy != PaymentPolicy.CAPTURE_ON_AUTH || authorizationEnabled)
            throw new IllegalStateException("Authorization activation requires enabled-account sandbox proof and financial integration");
    }
}
