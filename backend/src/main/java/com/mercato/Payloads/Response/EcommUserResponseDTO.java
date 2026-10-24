package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class EcommUserResponseDTO {
    private final String userId;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phoneNumber;
    private final String profileImageUrl;
    private final boolean enabled;
    private final boolean accountLocked;
    private final boolean emailVerified;
    private final Instant createdAt;
    private final Instant lastLoginAt;
    private final Instant deactivatedAt;
    private final boolean isSeller;
    private final String sellerDisplayName;
    private final List<String> roles;
    private final com.mercato.Entity.SellerStatus sellerStatus;
    private final boolean sellerSuspended;
    private final long accessVersion;
    private final boolean selfDeactivated;
}
