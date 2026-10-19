package com.mercato.Mapper;

import com.mercato.Entity.EcommUser;
import com.mercato.Payloads.Response.EcommUserResponseDTO;

import java.util.List;

public class EcommUserMapper {

    public static EcommUserResponseDTO toDTO(EcommUser user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getRoleName().name())
                .toList();

        return new EcommUserResponseDTO(
                user.getUserId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getProfileImageUrl(),
                user.isEnabled(),
                user.isAccountLocked(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getDeactivatedAt(),
                user.isSeller(),
                user.getSellerDisplayName(),
                roles,
                user.getSellerStatus() == null ? (user.isSeller() ? com.mercato.Entity.SellerStatus.APPROVED : com.mercato.Entity.SellerStatus.NONE) : user.getSellerStatus()
        );
    }
}
