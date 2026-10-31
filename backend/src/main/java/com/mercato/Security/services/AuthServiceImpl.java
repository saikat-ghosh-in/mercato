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

import com.mercato.Security.payloads.LoginRequest;
import com.mercato.Security.payloads.RegisterUserRequest;

import com.mercato.Service.CartService;

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



import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {


    private final com.mercato.Session.SessionService sessions;
    private final com.mercato.Auth.MfaService mfa;
    private final com.mercato.Auth.AbuseCounters abuse;
    private final org.springframework.core.env.Environment environment;
    private final com.mercato.Jobs.DurableJobs jobs;
    private final org.springframework.transaction.PlatformTransactionManager transactions;
    private final com.mercato.Session.SessionRevocation revocation;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final EmailVerificationService emailVerificationService;

    private final com.mercato.Service.SellerOnboardingService sellerOnboardingService;

    private CartService cartService;

    @Autowired
    public void setCartService(@Lazy CartService cartService) {
        this.cartService = cartService;
    }

    private String userNotFoundHash;

    @jakarta.annotation.PostConstruct
    void preparePasswordTiming() {
        userNotFoundHash=encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Override
    public ResponseEntity<?> authenticateUser(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        String identifier=loginRequest.getUsername().trim();
        String client=request.getRemoteAddr();
        int delay=abuse.consume("login-ip",client,1000,3600,false);
        var candidates=delay>0?java.util.List.<String>of():userRepository.findLoginUsernames(identifier);
        boolean resolved=candidates.size()==1;
        // All aliases share existing account-level abuse limits and MFA; ambiguous legacy identifiers fail closed.
        String username=resolved?candidates.get(0):identifier.toLowerCase(java.util.Locale.ROOT);
        delay=Math.max(delay,abuse.retryAfter("login-client",username+":"+client));
        delay=Math.max(delay,abuse.retryAfter("login-account-delay",username));
        if(delay>0) return ResponseEntity.status(429).header("Retry-After",Integer.toString(delay))
                .body(java.util.Map.of("code","AUTH_RETRY_LATER","message","Please wait before trying again."));
        String guest=(String)request.getSession().getAttribute(com.mercato.Session.SessionService.GUEST);
        SignIn proven=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
            EcommUser user=resolved?userRepository.findByUsernameForUpdate(username).orElse(null):null;
            if(user!=null&&!identifier.equals(user.getUsername())&&(user.getEmail()==null||!identifier.equalsIgnoreCase(user.getEmail())))user=null;
            boolean valid=encoder.matches(loginRequest.getPassword(),user==null?userNotFoundHash:user.getPassword());
            if(!valid || user==null || !user.isEmailVerified() || user.isAccountLocked()
                    || (!user.isEnabled()&&!user.isSelfDeactivated())) return null;
            if(!user.isEnabled()) {
                user.setEnabled(true);
                user.setSelfDeactivated(false);
                user.setDeactivatedAt(null);
                user.setAccessVersion(user.getAccessVersion()+1);
                revocation.revokeAll(user.getUserId());
                jobs.enqueue("AUTH_EMAIL","reactivated:"+user.getUserId()+":"+java.util.UUID.randomUUID(),
                        java.util.Map.of("to",user.getEmail(),"subject","Mercato account reactivated",
                                "text","Your account has been reactivated."));
            }
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
            String state="NONE";
            boolean demoBypass=java.util.Arrays.asList(environment.getActiveProfiles()).contains("demo")
                    && environment.getProperty("mercato.auth.mfa-bypass",Boolean.class,false);
            if(user.isAdmin()&&!demoBypass) state=mfa.enrolled(user.getUserId())?"CHALLENGE_REQUIRED":"ENROLL_REQUIRED";
            else if(!user.isAdmin()&&abuse.requiresStepUp(username)) state="STEP_UP_REQUIRED";
            if("NONE".equals(state)&&guest!=null) cartService.mergeGuestCartOnLogin(user.getUserId(),guest);
            return new SignIn(user,state);
        });
        if(proven==null) {
            abuse.failed(username);
            abuse.consume("login-client",username+":"+client,5,3600,true);
            abuse.consume("login-account-delay",username,10,3600,true);
            throw new org.springframework.security.authentication.BadCredentialsException("Unable to sign in with those credentials");
        }
        // Authentication and cart state have committed before a privileged cookie is created.
        sessions.establish(request,proven.user(),proven.state());
        if("NONE".equals(proven.state())) request.getSession().removeAttribute(com.mercato.Session.SessionService.GUEST);
        return ResponseEntity.ok(sessions.bootstrap(request));
    }

    private record SignIn(EcommUser user,String state) {}
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

        return "Account created. Your verification email is queued; delivery is not yet confirmed.";
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
        if (userRepository.existsByUsername(username) || userRepository.existsByEmailIgnoreCase(username))
            throw new ResourceAlreadyExistsException("User", "username", username);
        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByUsernameIgnoreCase(email))
            throw new ResourceAlreadyExistsException("User", "email", email);
    }
}
