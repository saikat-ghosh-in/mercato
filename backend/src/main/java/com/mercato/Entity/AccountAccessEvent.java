package com.mercato.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** Append-only access decisions. Identifiers survive account deactivation. */
@Entity @Getter @NoArgsConstructor
@Table(name = "account_access_events", indexes = @Index(name = "ix_access_subject_time", columnList = "subject_id,created_at"))
public class AccountAccessEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="subject_id", nullable=false, updatable=false, length=30) private String subjectId;
    @Column(nullable=false, updatable=false, length=30) private String actorId;
    @Column(nullable=false, updatable=false, length=20) private String actorUsername;
    @Column(nullable=false, updatable=false, length=40) private String action;
    @Column(nullable=false, updatable=false, length=500) private String reason;
    @Column(nullable=false, updatable=false, length=160) private String previousState;
    @Column(nullable=false, updatable=false, length=160) private String nextState;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    public AccountAccessEvent(EcommUser subject, EcommUser actor, String action, String reason, String previousState, String nextState) {
        this.subjectId=subject.getUserId(); this.actorId=actor.getUserId(); this.actorUsername=actor.getUsername();
        this.action=action; this.reason=reason; this.previousState=previousState; this.nextState=nextState; this.createdAt=Instant.now();
    }
}
