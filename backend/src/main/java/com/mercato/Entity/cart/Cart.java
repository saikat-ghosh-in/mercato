package com.mercato.Entity.cart;

import com.mercato.Entity.*;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(
        name = "carts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cart_cart_id", columnNames = "cart_id"),
                @UniqueConstraint(name = "uk_cart_guest_token", columnNames = "guest_token")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_seq")
    @SequenceGenerator(name = "cart_seq", sequenceName = "cart_seq", initialValue = 70000001, allocationSize = 10)
    private Long id;

    @Column(name = "cart_id", nullable = false, updatable = false, length = 30)
    private String cartId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_fk", referencedColumnName = "id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private EcommUser user;

    @Column(name = "guest_token", unique = true)
    private String guestToken;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 20)
    private DeliveryMethod deliveryMethod = DeliveryMethod.STANDARD;

    @Transient
    @Builder.Default
    private List<com.mercato.Payloads.Response.DeliveryOptionDTO> deliveryOptions = new ArrayList<>();

    @OneToMany(
            mappedBy = "cart",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true
    )
    @BatchSize(size = 30)
    @Builder.Default
    private List<CartItem> cartItems = new ArrayList<>();

    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @BatchSize(size = 20)
    @Builder.Default
    private List<CartCharge> charges = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;


    @PrePersist
    private void prePersist() {
        if (this.cartId == null) {
            String datePart = LocalDate.now().toString().replace("-", "");
            String randomPart = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase();
            this.cartId = "CART-" + datePart + "-" + randomPart;
        }
    }

    @Transient
    public BigDecimal getTotal() {
        return getSubtotal()
                .add(getTotalCharges())
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal getSubtotal() {
        return cartItems.stream()
                .map(item -> Optional.ofNullable(item.getLineTotal()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal getTotalCharges() {
        return charges.stream()
                .map(CartCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transient
    public boolean isEmpty() {
        return cartItems.isEmpty();
    }

    @Transient
    public int getCartQuantity() {
        return cartItems.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    public void addProduct(Product product, int quantity) {
        validateProduct(quantity, product);
        findItemByProductId(product.getProductId())
                .ifPresentOrElse(
                        item -> item.addQuantity(quantity),
                        () -> addCartItem(CartItem.builder()
                                .cart(this)
                                .product(product)
                                .quantity(quantity)
                                .unitPrice(product.getSellingPrice())
                                .build())
                );
    }

    public void updateProductQuantity(String productId, int quantity) {
        CartItem item = findItemByProductId(productId)
                .orElseThrow(() -> new IllegalStateException("Product not in cart: " + productId));
        if (quantity <= 0) {
            removeCartItem(item);
            return;
        }
        item.updateQuantity(quantity);
    }

    private void addCartItem(CartItem cartItem) {
        if (cartItem == null) return;
        cartItem.setCart(this);
        cartItems.add(cartItem);
    }

    public void removeCartItem(CartItem cartItem) {
        if (cartItem == null) return;
        cartItems.remove(cartItem);
        cartItem.setCart(null);
    }

    public void clear() {
        cartItems.forEach(item -> item.setCart(null));
        cartItems.clear();
    }

    public Optional<CartItem> findItemByProductId(String productId) {
        if (productId == null) return Optional.empty();
        return cartItems.stream()
                .filter(item -> productId.equals(item.getProduct().getProductId()))
                .findFirst();
    }

    private void validateProduct(int quantity, Product product) {
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be greater than 0, got: " + quantity);
        if (product == null)
            throw new IllegalArgumentException("Product must not be null");
        if (!product.isPurchasable())
            throw new IllegalStateException("Product is no longer available: " + product.getProductId());
    }

    public void addOrUpdateCharge(ChargeType type, BigDecimal amount, String description) {
        Optional<CartCharge> existing = findCharge(type);
        if (existing.isPresent()) {
            CartCharge charge = existing.get();
            charge.setAmount(amount);
            charge.setDescription(description);
        } else {
            CartCharge charge = CartCharge.builder()
                    .type(type)
                    .amount(amount)
                    .description(description)
                    .cart(this)
                    .build();

            charges.add(charge);
        }
    }

    public Optional<CartCharge> findCharge(ChargeType type) {
        return charges.stream()
                .filter(c -> c.getType() == type)
                .findFirst();
    }

    public BigDecimal getChargeAmount(ChargeType type) {
        return findCharge(type)
                .map(CartCharge::getAmount)
                .orElse(BigDecimal.ZERO);
    }

    public void removeCharge(ChargeType type) {
        findCharge(type).ifPresent(charge -> {
            charges.remove(charge);
            charge.setCart(null);
        });
    }

    public void clearCharges() {
        charges.forEach(c -> c.setCart(null));
        charges.clear();
    }
}
