package com.mercato.Entity.fulfillment;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;

@Entity @Table(name="fulfillment_work_items")
public class WorkflowWorkItemEntity {
    @Embeddable @NoArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name="order_id",nullable=false,length=30) String orderId;
        @Column(nullable=false,length=50) String kind;
    }
    @EmbeddedId Key id;
    @Column(nullable=false) Instant dueAt;
    @Column(nullable=false) Instant observedAt;
    @Column(nullable=false) int affectedQty;
    Instant resolvedAt;
    @Column(length=255) String resolvedBy;
    @Column(length=500) String resolutionReason;
    protected WorkflowWorkItemEntity() {}
}
