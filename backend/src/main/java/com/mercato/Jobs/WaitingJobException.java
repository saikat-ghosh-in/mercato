package com.mercato.Jobs;

import java.time.Duration;

/** A known pending state, including provider insufficient funds; retry after the supplied delay. */
public class WaitingJobException extends RuntimeException {
    private final Duration retryAfter;
    public WaitingJobException(String message, Duration retryAfter) {
        super(message);
        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero())
            throw new IllegalArgumentException("Waiting duration must be positive");
        this.retryAfter = retryAfter;
    }
    public Duration retryAfter() { return retryAfter; }
}
