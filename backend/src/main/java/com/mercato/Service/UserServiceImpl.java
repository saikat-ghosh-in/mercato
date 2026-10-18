package com.mercato.Service;

import com.mercato.Entity.AppRole;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Role;
import com.mercato.ExceptionHandler.CustomConflictException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.EcommUserMapper;
import com.mercato.Mapper.SellerMapper;
import com.mercato.Payloads.Request.UpdateProfileRequestDTO;
import com.mercato.Payloads.Response.AccountDeletionStatusResponseDTO;
import com.mercato.Payloads.Response.EcommUserResponseDTO;
import com.mercato.Payloads.Response.SellerResponseDTO;
import com.mercato.Repository.RoleRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    @Value("${mercato.account.cleanup.retention-days:7}")
    private int retentionDays;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthUtil authUtil;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<EcommUserResponseDTO> getAllUsers() {
        List<EcommUser> allUsers = userRepository.findAll();

        return allUsers.stream()
                .map(EcommUserMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public void updateUserRoles(String userId, Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty() || roleNames.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("At least one valid role is required");
        EcommUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        Set<Role> newRoles = new HashSet<>();
        for (String roleName : roleNames) {
            AppRole appRole;
            try {
                appRole = AppRole.valueOf(roleName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid role: " + roleName);
            }
            Role role = roleRepository.findByRoleName(appRole)
                    .orElseThrow(() -> new RuntimeException("Role not found in DB: " + roleName));
            newRoles.add(role);
        }
        user.setRoles(newRoles);
    }

    @Override
    public List<SellerResponseDTO> getAllSellers() {
        List<EcommUser> allSellers = userRepository.findUsersByRoleNames(List.of(AppRole.ROLE_SELLER));

        return allSellers.stream()
                .filter(seller -> seller.isEnabled() && !seller.isAccountLocked())
                .map(SellerMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public EcommUserResponseDTO updateProfile(UpdateProfileRequestDTO request) {
        EcommUser user = authUtil.getLoggedInUser();
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) throw new CustomConflictException("This phone number is already associated with another account");
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getProfileImageUrl() != null) user.setProfileImageUrl(request.getProfileImageUrl());
        if (request.getSellerDisplayName() != null
                && user.isSeller())
            user.setSellerDisplayName(request.getSellerDisplayName());
        userRepository.save(user);
        return EcommUserMapper.toDTO(user);
    }

    @Override
    @Transactional
    public void deactivateAccount() {
        EcommUser user = authUtil.getLoggedInUser();

        user.setEnabled(false);
        user.setDeactivatedAt(Instant.now());

        userRepository.save(user);
        log.info("User {} deactivated. Scheduled for deletion after {} days.",
                user.getUserId(), retentionDays);
    }

    @Override
    @Transactional
    public void deactivateUser(String userId, boolean deleteNow) {
        EcommUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        if (deleteNow) {
            userRepository.delete(user);
            log.info("Admin has permanently deleted user: {} with userId:{}",
                    user.getUsername(), user.getUserId());
            return;
        }

        user.setEnabled(false);
        user.setDeactivatedAt(Instant.now());

        userRepository.save(user);
        log.info("Admin has deactivated User {}. Scheduled for deletion after {} days.",
                user.getUserId(), retentionDays);
    }

    @Override
    @Transactional
    public void reactivateAccount(String userId) {
        EcommUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        if (user.getDeactivatedAt() == null) {
            throw new CustomConflictException("Account is not deactivated");
        }

        user.setEnabled(true);
        user.setDeactivatedAt(null);

        userRepository.save(user);

        emailService.sendReactivationConfirmationEmail(user.getEmail(), user.getUsername());
        log.info("User {} reactivated.", user.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDeletionStatusResponseDTO getAccountDeletionStatus() {
        EcommUser user = authUtil.getLoggedInUser();

        boolean isDeactivated = !user.isEnabled();
        long daysLeft = 0;

        if (isDeactivated) {
            if (user.getDeactivatedAt() != null) {
                Instant deletionDate = user.getDeactivatedAt().plus(7, ChronoUnit.DAYS);
                daysLeft = ChronoUnit.DAYS.between(Instant.now(), deletionDate);
            } else daysLeft = retentionDays;
        }

        return new AccountDeletionStatusResponseDTO(
                isDeactivated,
                Math.max(0, daysLeft)
        );
    }
}
