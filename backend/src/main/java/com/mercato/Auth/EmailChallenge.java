package com.mercato.Auth;
import jakarta.persistence.*;import lombok.Getter;import lombok.Setter;import java.time.Instant;
@Entity @Table(name="auth_email_challenges") @Getter @Setter
public class EmailChallenge {
 @Id @Column(length=80) private String userId;
 @Column(nullable=false,length=64) private String codeHash;
 @Column(nullable=false,length=64) private String sessionHash;
 @Column(nullable=false) private Instant expiresAt;
 private int attempts;
}
