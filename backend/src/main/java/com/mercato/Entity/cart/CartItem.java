package com.mercato.Entity.cart;

import com.mercato.Entity.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cart_item_cart_product", columnNames = {"cart_fk", "product_fk"})
        },
        indexes = {
                @Index(name = "idx_cart_item_cart_fk", columnList = "cart_fk"),
                @Index(name = "idx_cart_item_product_fk", columnList = "product_fk")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "cart_item_seq"
    )
    @SequenceGenerator(
            name = "cart_item_seq",
            sequenceName = "cart_item_seq",
            initialValue = 60000001,
            allocationSize = 10
    )
    private Long cartItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_fk", referencedColumnName = "id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_fk", referencedColumnName = "id", nullable = false)
    private Product product;

    @Column(nullable = false)
    @Min(1)
    private int quantity;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "out_of_stock", nullable = false)
    @Builder.Default
    private boolean outOfStock = false;

    @Transient
    @Builder.Default
    private BigDecimal sellerDeliveryCharge = BigDecimal.ZERO;


    @Transient
    public BigDecimal getItemPrice() {
        if (unitPrice != null) {
            return unitPrice;
        }
        return product.getSellingPrice();
    }

    @Transient
    public BigDecimal getLineTotal() {
        return getItemPrice()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void addQuantity(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        this.quantity += qty;
    }

    public void updateQuantity(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        this.quantity = qty;
    }
}
