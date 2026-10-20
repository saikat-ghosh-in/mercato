package com.mercato.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

/** Listing snapshots survive product edits/deletion; no live product relationship is serialized. */
@Entity @Table(name="listing_requests", indexes={@Index(name="idx_listing_request_seller",columnList="seller_id,requested_at"), @Index(name="idx_listing_request_product",columnList="product_id,status")})
@Getter @Setter @NoArgsConstructor
public class ListingRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="seller_id",nullable=false,length=30,updatable=false) private String sellerId;
    @Column(name="product_id",nullable=false,length=30,updatable=false) private String productId;
    @Column(nullable=false,updatable=false) private String productName;
    @Column(nullable=false,updatable=false) private String categoryName;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal price;
    @Column(nullable=false,length=20) private String status;
    @Column(name="requested_at",nullable=false,updatable=false) private Instant requestedAt;
    private Instant reviewedAt;
    @Column(length=30) private String reviewedBy;
    @Column(length=1000) private String note;
    @Column(nullable=false,updatable=false) private boolean imported;
}
