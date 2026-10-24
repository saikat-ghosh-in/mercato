package com.mercato.Entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "seller_notices", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "change_fk", "event_type"}))
@Getter @Setter @NoArgsConstructor
public class SellerNotice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, length = 30) private String userId;
    @Column(nullable = false, length = 100) private String email;
    @ManyToOne(optional = false) @JoinColumn(name = "change_fk", nullable = false) private CommissionChange change;
    @Column(name = "event_type", nullable = false, length = 20) private String eventType = "ANNOUNCED";
    @Column(nullable = false) private Instant createdAt;
    private Instant emailSentAt;
    private Instant readAt;
    private Instant nextAttemptAt;
    private int attempts;
    private boolean superseded;
}
