package com.mercato.Commerce;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="gateway_payment_attempt",uniqueConstraints=@UniqueConstraint(name="uk_provider_attempt",columnNames={"provider","provider_payment_id"}))
@Getter @Setter @NoArgsConstructor
public class GatewayAttempt {
    @Id @Column(length=150) private String id;
    @Column(nullable=false,length=30) private String orderId;
    @Column(nullable=false,length=20) private String provider;
    @Column(name="provider_payment_id",nullable=false,length=100) private String providerPaymentId;
    @Column(nullable=false,length=30) private String status;
    @Column(precision=15,scale=2) private BigDecimal amount;
    @Column(length=3) private String currency;
    private Instant capturedAt;
    @Column(nullable=false) private Instant updatedAt;
}
