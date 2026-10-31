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

    @Column(precision=10,scale=2)
    private BigDecimal selectedRetailPrice;
    @Column(precision=5,scale=2)
    private BigDecimal selectedDiscountPercent;
    @Column(length=40)
    private String selectedPriceProvenance;
    @Column(length=36,nullable=false)
    @Builder.Default
    private String selectionGeneration=java.util.UUID.randomUUID().toString();

    @Column(name = "out_of_stock", nullable = false)
    @Builder.Default
    private boolean outOfStock = false;

    @Transient
    @Builder.Default
    private BigDecimal sellerDeliveryCharge = BigDecimal.ZERO;
    @Transient @Builder.Default
    private BigDecimal buyerNormalDeliveryCharge=BigDecimal.ZERO;
    @Transient @Builder.Default
    private BigDecimal buyerExpeditedDeliveryCharge=BigDecimal.ZERO;


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
        this.quantity = Math.addExact(this.quantity,qty);
    }

    public void updateQuantity(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        if(qty<this.quantity)selectionGeneration=java.util.UUID.randomUUID().toString();
        this.quantity = qty;
    }

    public static CartItem selected(Cart cart,Product product,int quantity) {
        var item=CartItem.builder().cart(cart).product(product).quantity(quantity).unitPrice(product.getSellingPrice()).build();
        var retail=product.getRetailPrice();var percent=product.getDiscountPercent()==null?BigDecimal.ZERO:product.getDiscountPercent();
        if(retail!=null && item.unitPrice!=null && retail.multiply(BigDecimal.ONE.subtract(percent.movePointLeft(2))).setScale(2,RoundingMode.HALF_UP).compareTo(item.unitPrice)==0) {
            item.selectedRetailPrice=retail;item.selectedDiscountPercent=percent;item.selectedPriceProvenance="PRODUCT_PRICE_AT_SELECTION";
        }
        return item;
    }
    public CartItem copySelection(Cart target,Product current) {
        return CartItem.builder().cart(target).product(current).quantity(quantity).unitPrice(getItemPrice())
            .selectedRetailPrice(selectedRetailPrice).selectedDiscountPercent(selectedDiscountPercent)
            .selectedPriceProvenance(selectedPriceProvenance).build();
    }

}
