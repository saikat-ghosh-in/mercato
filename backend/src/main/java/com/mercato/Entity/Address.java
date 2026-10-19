package com.mercato.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "addresses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_address_address_id", columnNames = "address_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "address_seq"
    )
    @SequenceGenerator(
            name = "address_seq",
            sequenceName = "address_seq",
            initialValue = 50000001,
            allocationSize = 10
    )
    private Long id;

    @Column(name = "address_id", nullable = false, updatable = false, length = 30)
    private String addressId;

    @NotBlank
    @Column(nullable = false)
    private String recipientName;

    @NotBlank
    @Column(nullable = false, length = 15)
    private String recipientPhone;

    @NotBlank
    @Size(min = 3, message = "Address line 1 must be at least 3 characters")
    private String addressLine1;

    private String addressLine2;

    @NotBlank
    @Size(min = 2, message = "City name must be at least 2 characters")
    private String city;

    @NotBlank
    @Size(min = 2, message = "State name must be at least 2 characters")
    private String state;

    @NotBlank
    @Size(min = 5, message = "Pincode must be at least 5 characters")
    private String pincode;

    @NotBlank
    @Size(min = 2, message = "Country name must be at least 2 characters")
    private String country;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private EcommUser user;


    @PrePersist
    private void prePersist() {
        if (this.addressId == null) {
            String datePart = LocalDate.now().toString().replace("-", "");
            String randomPart = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 6).toUpperCase();
            this.addressId = "ADR-" + datePart + "-" + randomPart;
        }
    }
}
