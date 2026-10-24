package com.mercato.Jobs;

/** Deterministically invalid work; operator intervention is required. Messages are never persisted. */
public class PoisonJobException extends RuntimeException {
    public PoisonJobException(String message) { super(message); }
    public PoisonJobException(String message, Throwable cause) { super(message, cause); }
}
