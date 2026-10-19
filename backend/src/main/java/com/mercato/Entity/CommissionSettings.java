package com.mercato.Entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "commission_settings") @Getter @Setter @NoArgsConstructor
public class CommissionSettings {
    @Id private Long id = 1L;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal rate = new BigDecimal("5.00");
}
