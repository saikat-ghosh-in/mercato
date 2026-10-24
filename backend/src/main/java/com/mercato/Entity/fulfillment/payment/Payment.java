package com.mercato.Entity.fulfillment.payment;

import com.mercato.Entity.fulfillment.Order;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_payment_id", columnNames = "payment_id")
        },
        indexes = {
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_gateway_ref", columnList = "gateway_reference")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Payment {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "payment_seq"
    )
    @SequenceGenerator(
            name = "payment_seq",
            sequenceName = "payment_seq",
            initialValue = 200000001,
            allocationSize = 10
    )
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false, length = 30)
    private String paymentId;

    @OneToOne(mappedBy = "payment", optional = false)
    private Order order;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size=50)
    private java.util.List<Refund> refunds = new java.util.ArrayList<>();

    /** Compatibility presentation of the latest obligation; accounting uses every refund. */
    public Refund getRefund() {return refunds.isEmpty()?null:refunds.get(refunds.size()-1);}
    public void setRefund(Refund refund) {refunds.clear();if(refund!=null){refund.setPayment(this);refunds.add(refund);}}

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "gateway_reference", length = 100, unique = true)
    private String gatewayReference;

    @Column(name = "gateway_name", length = 50)
    private String gatewayName;

    @Column(name = "gateway_response_message")
    private String gatewayResponseMessage;

    @Column(name = "cf_order_id", length = 100)
    private String cfOrderId;

    @Column(name = "payment_session_id")
    private String paymentSessionId;

    private Instant initiatedAt;
    private Instant completedAt;

    @Column(precision=15,scale=2)
    private BigDecimal capturedAmount;
    @Column
    private Instant capturedAt;

    @PrePersist
    private void prePersist() {
        if (this.paymentId == null) {
            String datePart = LocalDate.now().toString().replace("-", "");
            String randomPart = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 6).toUpperCase();
            this.paymentId = "PMT-" + datePart + "-" + randomPart;
        }
    }
}
