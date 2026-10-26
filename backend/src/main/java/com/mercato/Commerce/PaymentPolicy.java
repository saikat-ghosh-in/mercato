package com.mercato.Commerce;

/** New checkout policy only. Account activation and delayed settlement are separate gated operations. */
public enum PaymentPolicy {
    CAPTURE_ON_AUTH, DELAYED_CAPTURE
}
