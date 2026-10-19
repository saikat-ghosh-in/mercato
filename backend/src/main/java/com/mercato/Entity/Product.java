package com.mercato.Entity;

import com.mercato.ExceptionHandler.InsufficientInventoryException;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_product_id", columnNames = "product_id")
        },
        indexes = {
                @Index(name = "idx_product_product_id", columnList = "product_id"),
                @Index(name = "idx_product_category", columnList = "category_id"),
                @Index(name = "idx_product_seller", columnList = "seller_fk"),
                @Index(name = "idx_product_active", columnList = "active"),
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", initialValue = 30000001, allocationSize = 10)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false, length = 30)
    private String productId;

    @Version
    @Column(nullable = false)
    private Long version;

    @NotBlank
    @Size(min = 4, message = "Product name must be at least 4 characters")
    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Null denotes a listing approved before moderation was introduced.
    @Column(name = "listing_status", length = 20)
    private String listingStatus;
    @Column(name = "listing_review_note", length = 1000)
    private String listingReviewNote;
    private Instant listingReviewedAt;
    private String listingReviewedBy;

    @Transient
    public boolean isPurchasable() {
        return active && (listingStatus == null || "APPROVED".equals(listingStatus))
            && (seller == null || (seller.isEnabled() && !seller.isAccountLocked()
                && (seller.getSellerStatus() == null || seller.getSellerStatus() == SellerStatus.APPROVED)));
    }

    @NotBlank(message = "Primary image is required")
    @Column(name = "primary_image_url", nullable = false, length = 500)
    private String primaryImageUrl;

    @ElementCollection
    @CollectionTable(
            name = "product_secondary_images",
            joinColumns = @JoinColumn(name = "product_id", nullable = false)
    )
    @Column(name = "image_url", length = 500)
    @OrderColumn(name = "image_order")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private List<String> secondaryImageUrls = new ArrayList<>();

    @NotBlank
    @Size(min = 7, message = "Product description must be at least 7 characters")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(length = 100)
    private String brand;
    @Column(name = "product_code", length = 100)
    private String productCode;
    // Packed measurements of one sellable unit, used for delivery pricing.
    @Column(precision = 8, scale = 3)
    private BigDecimal weightKg;
    @Column(precision = 8, scale = 2)
    private BigDecimal lengthCm;
    @Column(precision = 8, scale = 2)
    private BigDecimal widthCm;
    @Column(precision = 8, scale = 2)
    private BigDecimal heightCm;
    @Column(columnDefinition = "TEXT")
    private String materials;
    @Column(columnDefinition = "TEXT")
    private String care;
    @Column(columnDefinition = "TEXT")
    private String warranty;
    @Column(name = "package_contents", columnDefinition = "TEXT")
    private String packageContents;
    @Column(name = "delivery_returns", columnDefinition = "TEXT")
    private String deliveryReturns;

    @ElementCollection
    @CollectionTable(name = "product_highlights", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "highlight_order")
    @Column(name = "highlight", length = 300)
    @Builder.Default
    private List<String> highlights = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_specifications", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "spec_order")
    @Builder.Default
    private List<ProductSpecification> specifications = new ArrayList<>();

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(nullable = false)
    @Builder.Default
    private int physicalQty = 0;

    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private int reservedQty = 0;

    @NotNull(message = "Retail price is required")
    @DecimalMin(value = "0.01", message = "Retail price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Invalid price format")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal retailPrice;

    @DecimalMin(value = "0.00", message = "Discount must be at least 0%")
    @DecimalMax(value = "99.99", message = "Discount cannot exceed 99.99%")
    @Digits(integer = 2, fraction = 2, message = "Invalid discount format")
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @NotNull(message = "Category is required")
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_fk", referencedColumnName = "id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private EcommUser seller;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;


    @PrePersist
    private void prePersist() {
        if (this.productId == null) {
            String datePart = LocalDate.now().toString().replace("-", "");
            String randomPart = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 6).toUpperCase();
            this.productId = "PDT-" + datePart + "-" + randomPart;
        }
        this.sellingPrice = computeSellingPrice();
    }

    @PreUpdate
    private void preUpdate() {
        this.sellingPrice = computeSellingPrice();
    }

    private BigDecimal computeSellingPrice() {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return retailPrice.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discountRate = discountPercent.movePointLeft(2);
        return retailPrice
                .multiply(BigDecimal.ONE.subtract(discountRate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transient
    public int getAvailableQty() {
        return this.physicalQty - this.reservedQty;
    }

    public void adjustInventory(int delta) {
        int newQuantity = this.physicalQty + delta;
        if (newQuantity < 0) {
            throw new InsufficientInventoryException(this.productId, this.physicalQty);
        }
        this.physicalQty = newQuantity;
    }

    public void setInventoryAbsolute(int newQuantity) {
        if (newQuantity < 0)
            throw new IllegalArgumentException("Inventory cannot be negative");
        this.physicalQty = newQuantity;
    }

    public void adjustReservedQty(int delta) {
        int updated = this.reservedQty + delta;
        if (updated < 0) {
            throw new IllegalStateException("Reserved quantity cannot be negative");
        }
        if (updated > this.physicalQty) {
            throw new InsufficientInventoryException(this.productId, getAvailableQty());
        }
        this.reservedQty = updated;
    }

    public void addSecondaryImage(String imageUrl) {
        if (secondaryImageUrls == null) {
            secondaryImageUrls = new ArrayList<>();
        }
        if (secondaryImageUrls.size() >= 3) {
            throw new IllegalArgumentException("Maximum 3 secondary images allowed");
        }
        secondaryImageUrls.add(imageUrl);
    }

    public void removeSecondaryImage(String imageUrl) {
        if (secondaryImageUrls != null) {
            secondaryImageUrls.remove(imageUrl);
        }
    }

    public void clearSecondaryImages() {
        if (secondaryImageUrls != null) {
            secondaryImageUrls.clear();
        }
    }

    public List<String> getAllImages() {
        List<String> allImages = new ArrayList<>();
        if (primaryImageUrl != null) {
            allImages.add(primaryImageUrl);
        }
        if (secondaryImageUrls != null) {
            allImages.addAll(secondaryImageUrls);
        }
        return allImages;
    }
}
