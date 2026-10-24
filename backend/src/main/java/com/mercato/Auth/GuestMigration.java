package com.mercato.Auth;
import jakarta.persistence.*;import lombok.Getter;import lombok.Setter;import java.time.Instant;
@Entity @Table(name="auth_guest_migrations") @Getter @Setter
public class GuestMigration { @Id @Column(length=64) private String tokenHash; @Column(nullable=false) private Instant claimedAt; }
