package com.mercato.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static jakarta.persistence.CascadeType.*;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_user_business_id", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_user_phone", columnNames = "phone_number")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcommUser {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "user_seq"
    )
    @SequenceGenerator(
            name = "user_seq",
            sequenceName = "user_seq",
            initialValue = 10000001,
            allocationSize = 10
    )
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 30)
    private String userId;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String username;

    @NotBlank
    @Size(max = 100)
    @Email
    @Column(nullable = false, length = 100)
    private String email;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String password;

    @Size(max = 50)
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50)
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Pattern(regexp = "^[0-9]{10}$", message = "Invalid phone number")
    @Column(name = "phone_number", length = 10)
    private String phoneNumber;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Builder.Default
    @Column(name = "self_deactivated", nullable = false)
    private boolean selfDeactivated = false;

    @Builder.Default
    @Column(name = "seller_suspended", nullable = false)
    private boolean sellerSuspended = false;

    @Builder.Default
    @Column(name = "access_version", nullable = false)
    private long accessVersion = 0;

    @Size(max = 100)
    @Column(name = "seller_display_name", length = 100)
    private String sellerDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_status", length = 20)
    private SellerStatus sellerStatus;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = ALL, orphanRemoval = true)
    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "seller", cascade = {PERSIST, MERGE})
    @Builder.Default
    private Set<Product> products = new HashSet<>();


    @PrePersist
    private void prePersist() {
        if (this.userId == null) {
            String datePart = LocalDate.now().toString().replace("-", "");
            String randomPart = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase();
            this.userId = "USR-" + datePart + "-" + randomPart;
        }
    }

    @Transient
    public String getFullName() {
        if (firstName == null && lastName == null) return username;
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    @Transient
    public boolean isSeller() {
        return roles.stream().anyMatch(r -> r.getRoleName() == AppRole.ROLE_SELLER);
    }

    @Transient
    public boolean isAdmin() {
        return roles.stream().anyMatch(r -> r.getRoleName() == AppRole.ROLE_ADMIN);
    }

    public void addAddress(Address address) {
        if (address == null) return;
        addresses.add(address);
        address.setUser(this);
    }

    public void removeAddress(Address address) {
        if (address == null) return;
        addresses.remove(address);
        address.setUser(null);
    }

    public void addProduct(Product product) {
        if (product == null) return;
        products.add(product);
        product.setSeller(this);
    }

    public void removeProduct(Product product) {
        if (product == null) return;
        products.remove(product);
        product.setSeller(null);
    }
}
