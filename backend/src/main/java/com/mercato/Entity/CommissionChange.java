package com.mercato.Entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "commission_changes") @Getter @Setter @NoArgsConstructor
public class CommissionChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal previousRate;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal rate;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false, length = 30) private String createdBy;
    @Column(nullable = false) private Instant createdAt;
    private Instant effectiveAt;
    @Column(nullable = false, length = 20) private String status = "NOTIFYING";
}
