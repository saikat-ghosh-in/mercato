package com.mercato.Auth;
import jakarta.persistence.*;import lombok.Getter;import lombok.Setter;import java.time.Instant;
@Entity @Table(name="auth_login_failures",indexes=@Index(name="auth_login_failure_account_time",columnList="account_hash,failed_at")) @Getter @Setter
public class LoginFailure {
 @Id @Column(length=36) private String id;
 @Column(name="account_hash",nullable=false,length=64) private String accountHash;
 @Column(name="failed_at",nullable=false) private Instant failedAt;
}
