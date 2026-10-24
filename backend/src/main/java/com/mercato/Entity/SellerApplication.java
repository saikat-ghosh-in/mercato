package com.mercato.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "seller_applications")
@Getter @Setter @NoArgsConstructor
public class SellerApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_fk", unique = true, nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private EcommUser user;
    @Column(nullable = false, length = 150) private String legalBusinessName;
    @Column(nullable = false, length = 40) private String businessType;
    @Column(nullable = false, length = 10) private String pan;
    @Column(length = 15) private String gstin;
    @Column(nullable = false, length = 30) private String taxRegistrationType;
    @Column(length = 30) private String enrolmentNumber;
    @Column(nullable = false, length = 500) private String pickupAddress;
    @Column(nullable = false, length = 80) private String pickupState;
    @Column(nullable = false, length = 6) private String pickupPincode;
    @Column(nullable = false, length = 100) private String supportEmail;
    @Column(nullable = false, length = 300) private String productCategories;
    @Column(nullable = false, length = 40) private String policyVersion;
    @Column(nullable = false) private Instant submittedAt;
    private Instant reviewedAt;
    @Column(length = 30) private String reviewedBy;
    @Column(length = 1000) private String reviewNote;
}
