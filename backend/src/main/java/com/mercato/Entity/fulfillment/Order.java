package com.mercato.Entity.fulfillment;

import com.mercato.Entity.fulfillment.payment.Payment;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(
        name = "order_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_order_id", columnNames = "order_id"),
                @UniqueConstraint(name = "uk_order_payment_fk", columnNames = "payment_fk")
        },
        indexes = {
                @Index(name = "idx_order_id", columnList = "order_id"),
                @Index(name = "idx_order_status", columnList = "order_status"),
                @Index(name = "idx_order_customer_email", columnList = "customer_email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Slf4j
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_seq", initialValue = 80000001, allocationSize = 1)
    @EqualsAndHashCode.Include
    private Long id;

    @Size(max = 30)
    @Column(name = "order_id", nullable = false, updatable = false, length = 30)
    private String orderId;

    @Column(name = "customer_name", nullable = false, updatable = false)
    private String customerName;

    @Column(name = "customer_email", nullable = false, updatable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false, length = 15)
    private String recipientPhone;

    @Column(nullable = false, updatable = false)
    private String deliveryAddressLine1;

    @Column(updatable = false)
    private String deliveryAddressLine2;

    @Column(nullable = false, updatable = false)
    private String deliveryCity;

    @Column(nullable = false, updatable = false)
    private String deliveryState;

    @Column(nullable = false, updatable = false)
    private String deliveryPincode;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", updatable = false, length = 20)
    private com.mercato.Entity.cart.DeliveryMethod deliveryMethod;

    @Column(name = "delivery_estimate", updatable = false, length = 100)
    private String deliveryEstimate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    private List<OrderLine> orderLines = new ArrayList<>();

    @Column(length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal charges;
    @Column(name = "shipping_charge", precision = 15, scale = 2, updatable = false)
    private BigDecimal shippingCharge;
    @Column(name = "platform_fee", precision = 15, scale = 2, updatable = false)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "payment_fk", referencedColumnName = "id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "shipment_email_sent_at")
    private Instant shipmentEmailSentAt;

    @Column(name = "inventory_finalization_failed", nullable = false)
    @ColumnDefault("false")
    private boolean inventoryFinalizationFailed = false;


    @Transient
    public BigDecimal getRevenue() {
        return orderLines.stream()
                .map(OrderLine::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void attachPayment(Payment payment) {
        if (this.orderStatus != OrderStatus.CREATED) {
            throw new IllegalStateException("Cannot attach payment after order is confirmed");
        }
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }

        this.payment = payment;
        payment.setOrder(this);
    }

    public void confirmOrder() {
        if (this.orderStatus != OrderStatus.CREATED) {
            log.warn("Cannot confirm order in status: {}", this.orderStatus);
            return;
        }
        this.orderStatus = OrderStatus.CONFIRMED;
        this.paymentStatus = PaymentStatus.SUCCESS;
        this.orderLines.forEach(line -> line.setOrderLineStatus(OrderLineStatus.CONFIRMED));
    }

    public void addOrderLine(OrderLine orderLine) {
        this.orderLines.add(orderLine);
        orderLine.setOrder(this);
    }
}
