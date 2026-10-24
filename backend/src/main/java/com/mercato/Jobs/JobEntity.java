package com.mercato.Jobs;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Schema mapping for demo H2 creation; deployed PostgreSQL uses the reviewed additive SQL. */
@Entity
@Table(name="durable_jobs", indexes={@Index(name="ix_durable_jobs_due",columnList="status,due_at"),
        @Index(name="ix_durable_jobs_lease",columnList="status,lease_expires_at")})
public class JobEntity {
    @Id @Column(nullable=false) UUID id;
    @Column(name="job_type",nullable=false,length=120) String type;
    @Column(name="business_key",nullable=false,unique=true,length=255) String businessKey;
    @Column(name="payload",nullable=false,columnDefinition="text") String payload;
    @Column(name="payload_version",nullable=false) int payloadVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) JobStatus status;
    @Column(nullable=false) int attempts;
    @Column(name="due_at",nullable=false) Instant dueAt;
    @Column(name="lease_owner",length=120) String leaseOwner;
    @Column(name="lease_token") UUID leaseToken;
    @Column(name="lease_expires_at") Instant leaseExpiresAt;
    @Column(name="failure_fingerprint",length=64) String failureFingerprint;
    @Column(name="failure_count",nullable=false) int failureCount;
    @Column(name="last_failure",length=512) String lastFailure;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected JobEntity() {}
}
