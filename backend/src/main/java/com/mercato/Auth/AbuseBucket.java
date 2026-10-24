package com.mercato.Auth;
import jakarta.persistence.*;
import lombok.Getter;import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="auth_abuse_buckets") @Getter @Setter
public class AbuseBucket {
 @Id @Column(length=64) private String bucketKey;
 @Column(nullable=false) private Instant windowStart;
 private int attempts;
 private Instant blockedUntil;
}
