package com.mercato.Entity.fulfillment;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;

/** Demo schema mapping; PostgreSQL constraints and backfill are defined by V9. */
@Entity @Table(name="fulfillment_commands")
public class WorkflowCommandEntity {
    @Embeddable @NoArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name="order_id",nullable=false,length=30) String orderId;
        @Column(name="operation_id",nullable=false,length=120) String operationId;
    }
    @EmbeddedId Key id;
    @Column(nullable=false,columnDefinition="text") String requestBody;
    @Column(nullable=false,columnDefinition="text") String outcome;
    @Column(nullable=false,length=20) String actorType;
    @Column(nullable=false,length=255) String actorIdentity;
    @Column(length=500) String reason;
    @Column(length=50) String reasonCode;
    @Column(length=30) String fulfillmentId;
    Integer cancellationOrdinalFrom;
    Integer cancellationOrdinalTo;
    @Column(nullable=false,length=30) String action;
    @Column(nullable=false) int lineNumber;
    @Column(nullable=false) int qty;
    @Column(nullable=false) int unacceptedQty;
    @Column(nullable=false) int acceptedOnlyQty;
    @Column(nullable=false) int preparingOnlyQty;
    @Column(nullable=false) int packedOnlyQty;
    @Column(nullable=false) Instant createdAt;
    protected WorkflowCommandEntity() {}
}
