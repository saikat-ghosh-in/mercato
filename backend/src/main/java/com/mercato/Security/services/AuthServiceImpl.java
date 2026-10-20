package com.mercato.Security.services;

import com.mercato.Entity.AppRole;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Role;
import com.mercato.ExceptionHandler.ResourceAlreadyExistsException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.EcommUserMapper;
import com.mercato.Payloads.Response.EcommUserResponseDTO;
import com.mercato.Repository.RoleRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Security.jwt.JwtUtils;
import com.mercato.Security.payloads.LoginRequest;
import com.mercato.Security.payloads.RegisterUserRequest;
import com.mercato.Security.payloads.UserInfoResponse;
import com.mercato.Service.CartService;
import com.mercato.Service.EmailService;
import com.mercato.Service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;
    private final com.mercato.Service.SellerOnboardingService sellerOnboardingService;

    private CartService cartService;

    @Autowired
    public void setCartService(@Lazy CartService cartService) {
        this.cartService = cartService;
    }

    @Override
    @Transactional
    public ResponseEntity<UserInfoResponse> authenticateUser(LoginRequest loginRequest,
                                                             HttpServletRequest request,
                                                             HttpServletResponse httpServletResponse) {
        EcommUser user = userRepository.findByUsernameForUpdate(loginRequest.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", loginRequest.getUsername()));

        if (!user.isEmailVerified()) {
            throw new DisabledException("Email not verified. Please check your email and verify your account.");
        }

        if (user.isAccountLocked()) {
            throw new DisabledException("Account is locked. Please contact support.");
        }

        if (!user.isEnabled() && user.isSelfDeactivated() && encoder.matches(loginRequest.getPassword(), user.getPassword())) {
            user.setEnabled(true);
            user.setSelfDeactivated(false);
            user.setDeactivatedAt(null);
            userRepository.save(user);
            log.info("User {} auto-reactivated on login", user.getUsername());
            emailService.sendReactivationConfirmationEmail(user.getEmail(), user.getFirstName());
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String guestToken = JwtUtils.extractGuestToken(request);
        if (guestToken != null) {
            cartService.mergeGuestCartOnLogin(userDetails.getUserId(), guestToken);
        }

        String jwt = jwtUtils.generateJwtToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        UserInfoResponse response = UserInfoResponse.builder()
                .userId(userDetails.getUserId())
                .username(userDetails.getUsername())
                .email(userDetails.getEmail())
                .token(jwt)
                .tokenExpirationTime(jwtUtils.getTokenExpirationTime(jwt))
                .roles(roles)
                .build();

        return ResponseEntity.ok(response);
    }

    @Override
    @Transactional
    public String registerNewUser(RegisterUserRequest registerUserRequest) {
        throwIfAnExistingUser(registerUserRequest.getUsername(), registerUserRequest.getEmail());

        EcommUser user = EcommUser.builder()
                .username(registerUserRequest.getUsername())
                .email(registerUserRequest.getEmail())
                .password(encoder.encode(registerUserRequest.getPassword()))
                .firstName(registerUserRequest.getFirstName())
                .lastName(registerUserRequest.getLastName())
                .phoneNumber(registerUserRequest.getPhoneNumber())
                .profileImageUrl("placeholder")
                .enabled(true)
                .emailVerified(false)
                .build();

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        roles.add(userRole);
        user.setRoles(roles);

        EcommUser savedUser = userRepository.save(user);
        if ("SELLER".equals(registerUserRequest.getAccountType()))
            sellerOnboardingService.submit(savedUser, registerUserRequest.getSellerApplication());

        emailVerificationService.createAndSendVerificationToken(savedUser);

        return "User registered successfully! Please check your email to verify your account.";
    }

    @Override
    public String getCurrentUsernameFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return null;
    }

    @Override
    public EcommUserResponseDTO getCurrentUserFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserDetailsImpl userDetails)) {
            throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
        }

        EcommUser user = getUserByUserId(userDetails.getUserId());
        return EcommUserMapper.toDTO(user);
    }

    @Override
    public ResponseEntity<?> signOutCurrentUser() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("You have been signed out!");
    }

    private EcommUser getUserByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
    }

    private void throwIfAnExistingUser(String username, String email) {
        if (userRepository.existsByUsername(username))
            throw new ResourceAlreadyExistsException("User", "username", username);
        if (userRepository.existsByEmail(email))
            throw new ResourceAlreadyExistsException("User", "email", email);
    }
}
