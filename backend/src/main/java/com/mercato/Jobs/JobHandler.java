package com.mercato.Jobs;

/**
 * At-least-once delivery: reconcile external state using the stable business key before repeating
 * an ambiguous side effect. No database claim transaction remains open while handle executes.
 */
public interface JobHandler {
    String type();
    void handle(JobRecord job);
}
