package com.mercato.Entity.fulfillment;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Builder
@Entity
@Table(
        name = "order_lines_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_line_order_line_number", columnNames = {"order_fk", "order_line_number"})
        },
        indexes = {
                @Index(name = "idx_order_line_fulfillment_id_line_number", columnList = "fulfillment_id, order_line_number"),
                @Index(name = "idx_order_line_order_fk", columnList = "order_fk"),
                @Index(name = "idx_order_line_product_id", columnList = "product_id"),
                @Index(name = "idx_order_line_status", columnList = "order_line_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_line_seq")
    @SequenceGenerator(name = "order_line_seq", sequenceName = "order_line_seq", initialValue = 90000001, allocationSize = 1)
    @EqualsAndHashCode.Include
    private Long id;

    @Size(max = 30)
    @Column(name = "fulfillment_id", nullable = false, updatable = false, length = 30)
    private String fulfillmentId;

    @NotNull
    @Min(1)
    @Column(name = "order_line_number", nullable = false, updatable = false)
    private int orderLineNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_fk", referencedColumnName = "id", nullable = false)
    private Order order;

    @OneToOne(mappedBy = "orderLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private OrderReservation orderReservation;

    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    @Column(nullable = false, updatable = false)
    private String productName;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "ordered_qty", nullable = false, updatable = false)
    @Min(1)
    private int orderedQty;

    @Column(name = "accepted_qty", nullable=false)
    private int acceptedQty;

    @Column(name = "shipped_qty", nullable = false)
    @Builder.Default
    private int shippedQty = 0;

    @Column(nullable = false)
    private int preparingQty;
    @Column(nullable=false)
    private boolean workflowLegacyReview;
    private Integer workflowBaselineOrderedQty;
    private Integer workflowBaselineAcceptedQty;
    private Instant workflowBaselineAt;
    @Column(nullable = false)
    private int packedQty;
    @Column(nullable = false)
    private int deliveredQty;

    @Column(name = "cancelled_qty", nullable = false)
    @Builder.Default
    private int cancelledQty = 0;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal lineTotal;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal revenue = BigDecimal.valueOf(0);

    @Column(updatable = false, length = 100)
    private String sellerName;

    @Column(updatable = false, length = 100)
    private String sellerEmail;

    @Column(name = "commission_rate_percent", precision = 5, scale = 2, updatable = false)
    private BigDecimal commissionRatePercent;
    @Column(name = "commission_cap_per_item", precision = 10, scale = 2, updatable = false)
    private BigDecimal commissionCapPerItem;
    // Full-line standard-delivery contribution captured at checkout. Legacy null means no contribution.
    @Column(name = "seller_delivery_charge", precision = 12, scale = 2, updatable = false)
    private BigDecimal sellerDeliveryCharge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderLineStatus orderLineStatus;

    @OneToMany(mappedBy = "orderLine", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<StateTransition> stateTransitions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;


    @Transient
    public int getPendingQty() {
        return orderedQty - shippedQty - cancelledQty;
    }

    @Transient
    public OrderLineStatus deriveStatus() {
        int pending = getPendingQty();

        if (cancelledQty == orderedQty)
            return OrderLineStatus.CANCELLED;

        if (shippedQty == orderedQty)
            return OrderLineStatus.FULFILLED;

        if (pending == 0 && shippedQty > 0 && cancelledQty > 0)
            return OrderLineStatus.PARTIALLY_FULFILLED;

        if (pending > 0 && (shippedQty > 0 || cancelledQty > 0))
            return OrderLineStatus.PARTIALLY_PROCESSED;

        return orderLineStatus;
    }

    public void addStateTransition(StateTransition transition) {
        stateTransitions.add(transition);
        transition.setOrderLine(this);
    }

    public boolean isTerminal() {
        return getPendingQty() == 0;
    }

    public boolean hasPendingQty() {
        return getPendingQty() > 0;
    }

    public void accept(int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Accepted qty must be greater than 0");
        if (qty > orderedQty - cancelledQty - acceptedQty)
            throw new IllegalArgumentException(
                    "Cannot accept more than unaccepted qty: " + (orderedQty - cancelledQty - acceptedQty)
            );
        this.acceptedQty += qty;
    }

    public void startPreparing(int qty) {
        if (qty <= 0 || qty > acceptedQty - preparingQty) throw new IllegalArgumentException("Preparing quantity exceeds accepted units");
        preparingQty += qty;
    }
    public void markPacked(int qty) {
        if (qty <= 0 || qty > preparingQty - packedQty) throw new IllegalArgumentException("Packed quantity exceeds preparing units");
        packedQty += qty;
    }
    public boolean validQuantities() {
        return 0 <= deliveredQty && deliveredQty <= shippedQty && shippedQty <= packedQty
            && packedQty <= preparingQty && preparingQty <= acceptedQty && acceptedQty <= orderedQty - cancelledQty && cancelledQty >= 0;
    }
    public void ship(int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Shipped qty must be greater than 0");
        if (qty > packedQty - shippedQty)
            throw new IllegalArgumentException(
                    "Cannot ship more than packed qty: " + (packedQty - shippedQty)
            );
        this.shippedQty += qty;
        this.revenue = this.unitPrice.multiply(BigDecimal.valueOf(this.shippedQty));
    }

    public void cancel(int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Cancelled qty must be greater than 0");
        if (qty > getPendingQty())
            throw new IllegalArgumentException(
                    "Cannot cancel more than pending qty: " + getPendingQty()
            );
        this.cancelledQty += qty;
        // Consume least advanced units first; shipped history is immutable.
        int remaining = orderedQty - cancelledQty;
        acceptedQty = Math.min(acceptedQty, remaining);
        preparingQty = Math.min(preparingQty, acceptedQty);
        packedQty = Math.min(packedQty, preparingQty);
    }
}
