package com.mercato.Jobs;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A delivery snapshot. Lease token must accompany every state-changing worker operation. */
public record JobRecord(UUID id, String type, String businessKey, Map<String,Object> payload,
        int payloadVersion, JobStatus status, int attempts, Instant dueAt, String leaseOwner,
        UUID leaseToken, Instant leaseExpiresAt, String failureFingerprint, int failureCount,
        String lastFailure, Instant createdAt, Instant updatedAt) {
    public JobRecord { payload = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload)); }
}
