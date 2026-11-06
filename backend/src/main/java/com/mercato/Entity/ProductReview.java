package com.mercato.Entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_review", uniqueConstraints =
        @UniqueConstraint(name = "uk_product_review_buyer", columnNames = {"product_id", "buyer_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String productId;
    @Column(nullable = false, length = 100)
    private String buyerId;
    @Column(nullable = false)
    private Long orderLineId;
    @Column(nullable = false)
    private int rating;
    @Column(nullable = false, length = 2000)
    private String reviewText;
    @Column(length = 255)
    private String videoUrl;
    @Column(nullable = false)
    private Instant createdAt;

    @ElementCollection
    @BatchSize(size = 10)
    @CollectionTable(name = "product_review_photo", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "position", nullable = false)
    @Column(name = "photo_url", nullable = false, length = 255)
    private List<String> photoUrls = new ArrayList<>();

    public ProductReview(String productId, String buyerId, Long orderLineId, int rating, String reviewText,
                         List<String> photoUrls, String videoUrl, Instant createdAt) {
        this.productId = productId;
        this.buyerId = buyerId;
        this.orderLineId = orderLineId;
        this.rating = rating;
        this.reviewText = reviewText;
        this.photoUrls = new ArrayList<>(photoUrls);
        this.videoUrl = videoUrl;
        this.createdAt = createdAt;
    }
}
