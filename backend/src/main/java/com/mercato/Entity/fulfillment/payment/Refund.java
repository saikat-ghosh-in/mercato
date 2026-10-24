package com.mercato.Entity.fulfillment.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "refunds",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refund_refund_id", columnNames = "refund_id")
        },
        indexes = {
                @Index(name = "idx_refund_payment_fk", columnList = "payment_fk"),
                @Index(name = "idx_refund_status", columnList = "status"),
                @Index(name = "idx_refund_gateway_ref", columnList = "gateway_reference")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refund_seq")
    @SequenceGenerator(name = "refund_seq", sequenceName = "refund_seq", initialValue = 250000001, allocationSize = 1)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "refund_id", nullable = false, updatable = false)
    private String refundId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_fk", referencedColumnName = "id", nullable = false)
    private Payment payment;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    private String reason;

    @Column(name = "gateway_response_message", columnDefinition = "TEXT")
    private String gatewayResponseMessage;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
