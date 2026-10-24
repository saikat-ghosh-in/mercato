package com.mercato.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="seller_promotions", indexes={@Index(name="idx_promotion_window",columnList="starts_at,ends_at"),@Index(name="idx_promotion_seller",columnList="seller_id")})
@Getter @Setter
public class SellerPromotion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=36) private String requestId;
    @Column(name="seller_id",nullable=false) private String sellerId;
    @Column(nullable=false,length=30) private String productId;
    @Column(nullable=false,length=20) private String tier;
    @Column(nullable=false) private int durationDays;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal dailyRate;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal fee;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal credited = BigDecimal.ZERO;
    @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false,length=20) private String status = "BOOKED";
    private Instant cancelledAt;
}
