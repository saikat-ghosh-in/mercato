package com.mercato.Auth;
import jakarta.persistence.*;
import lombok.Getter;import lombok.Setter;
import java.time.Instant;import java.util.HashSet;import java.util.Set;
@Entity @Table(name="auth_mfa_credentials") @Getter @Setter
public class MfaCredential {
 @Id @Column(length=80) private String userId;
 @Column(length=512) private String encryptedSeed;
 @Column(length=512) private String pendingSeed;
 private Instant pendingExpiresAt;
 private long lastAcceptedStep=-1;
 private Instant enrolledAt;
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="auth_mfa_recovery_codes",joinColumns=@JoinColumn(name="user_id")) @Column(name="code_hash",length=64,nullable=false)
 private Set<String> recoveryHashes=new HashSet<>();
}
