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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthUtil authUtil;
    private final EmailService emailService;
    private final com.mercato.Session.SessionRevocation sessions;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

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

        protectAdmin(user);
        if (!roleNames.contains("ROLE_USER")) throw new IllegalArgumentException("The base user role cannot be removed");
        throw new IllegalArgumentException("Use audited account access management to change selling access");
    }

    @Override
    public List<SellerResponseDTO> getAllSellers() {
        List<EcommUser> allSellers = userRepository.findUsersByRoleNames(List.of(AppRole.ROLE_SELLER));

        return allSellers.stream()
                .filter(seller -> seller.isEnabled() && !seller.isAccountLocked() && !seller.isSellerSuspended())
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
        EcommUser user = userRepository.findByUserIdForUpdate(authUtil.getLoggedInUser().getUserId()).orElseThrow();
        entityManager.refresh(user); // Re-read after the lock; never overwrite a concurrent admin decision.

        protectAdmin(user);
        if (!user.isEnabled()) throw new org.springframework.security.access.AccessDeniedException("Account is already deactivated");
        user.setEnabled(false);
        user.setSelfDeactivated(true);
        user.setAccessVersion(user.getAccessVersion()+1);
        sessions.revokeAll(user.getUserId());
        user.setDeactivatedAt(Instant.now());

        userRepository.save(user);
        log.info("User {} paused their account; history retained.", user.getUserId());
    }

    @Override
    @Transactional
    public void deactivateUser(String userId, boolean deleteNow) {
        EcommUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        protectAdmin(user);

        throw new IllegalArgumentException("Use audited account access management; accounts are retained");
    }

    @Override
    @Transactional
    public void reactivateAccount(String userId) {
        EcommUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        protectAdmin(user);
        throw new IllegalArgumentException("Use audited account access management to restore an account");
    }

    private void protectAdmin(EcommUser user) {
        if (user.isAdmin()) throw new org.springframework.security.access.AccessDeniedException("Administrator accounts are read-only in user management");
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDeletionStatusResponseDTO getAccountDeletionStatus() {
        EcommUser user = authUtil.getLoggedInUser();

        // Compatibility endpoint: no account is scheduled for automatic deletion.
        return new AccountDeletionStatusResponseDTO(!user.isEnabled(), 0);
    }
}
