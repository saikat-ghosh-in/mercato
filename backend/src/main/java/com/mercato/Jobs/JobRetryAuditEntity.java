package com.mercato.Jobs;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="durable_job_retry_audit",indexes=@Index(name="ix_job_retry_audit_job",columnList="job_id,created_at"))
public class JobRetryAuditEntity {
    @Id @Column(nullable=false) UUID id;
    @Column(name="job_id",nullable=false) UUID jobId;
    @Column(nullable=false,length=120) String actor;
    @Column(nullable=false,length=500) String reason;
    @Column(name="previous_status",nullable=false,length=24) String previousStatus;
    @Column(name="created_at",nullable=false) Instant createdAt;
    protected JobRetryAuditEntity() {}
}
