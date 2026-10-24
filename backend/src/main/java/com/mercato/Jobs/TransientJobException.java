package com.mercato.Jobs;

/** Known infrastructure/provider failure eligible for the standard retry schedule. */
public class TransientJobException extends RuntimeException {
    public TransientJobException(String message) { super(message); }
    public TransientJobException(String message, Throwable cause) { super(message, cause); }
}
