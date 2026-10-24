package com.mercato.Service;

import com.mercato.Entity.*;
import com.mercato.Payloads.Request.SellerApplicationRequest;
import com.mercato.Repository.*;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class SellerOnboardingService {
    private final SellerApplicationRepository applications;
    private final UserRepository users;
    private final RoleRepository roles;
    private final AuthUtil auth;
    private final CommissionService commissions;
    private final AccountAccessEventRepository accessEvents;
    private final com.mercato.Session.SessionRevocation sessions;

    @Transactional
    public void submit(EcommUser user, SellerApplicationRequest request) {
        if (request == null) throw new IllegalArgumentException("Seller details are required");
        SellerApplication application = new SellerApplication();
        application.setUser(user);
        application.setLegalBusinessName(request.getLegalBusinessName().trim());
        application.setBusinessType(request.getBusinessType());
        application.setPan(request.getPan());
        application.setGstin(request.getGstin());
        application.setTaxRegistrationType(request.getTaxRegistrationType());
        application.setEnrolmentNumber(request.getEnrolmentNumber());
        application.setPickupAddress(request.getPickupAddress().trim());
        application.setPickupState(request.getPickupState().trim());
        application.setPickupPincode(request.getPickupPincode());
        application.setSupportEmail(request.getSupportEmail());
        application.setProductCategories(request.getProductCategories().trim());
        application.setPolicyVersion(request.getPolicyVersion());
        application.setSubmittedAt(Instant.now());
        user.setSellerDisplayName(request.getStoreName().trim());
        user.setSellerStatus(SellerStatus.PENDING);
        applications.save(application);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return applications.findAll().stream().sorted(Comparator.comparing(SellerApplication::getSubmittedAt).reversed())
                .map(this::details).toList();
    }

    private Map<String, Object> details(SellerApplication a) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("userId", a.getUser().getUserId()); dto.put("username", a.getUser().getUsername());
        dto.put("adminAccount", a.getUser().isAdmin());
        dto.put("storeName", a.getUser().getSellerDisplayName()); dto.put("emailVerified", a.getUser().isEmailVerified());
        dto.put("status", a.getUser().getSellerStatus()); dto.put("legalBusinessName", a.getLegalBusinessName());
        dto.put("businessType", a.getBusinessType()); dto.put("pan", a.getPan()); dto.put("gstin", a.getGstin());
        dto.put("taxRegistrationType", a.getTaxRegistrationType()); dto.put("enrolmentNumber", a.getEnrolmentNumber());
        dto.put("pickupAddress", a.getPickupAddress()); dto.put("pickupState", a.getPickupState()); dto.put("pickupPincode", a.getPickupPincode());
        dto.put("supportEmail", a.getSupportEmail()); dto.put("productCategories", a.getProductCategories());
        dto.put("policyVersion", a.getPolicyVersion()); dto.put("submittedAt", a.getSubmittedAt());
        dto.put("reviewNote", a.getReviewNote()); dto.put("reviewedAt", a.getReviewedAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myApplication() {
        return applications.findByUser_UserId(auth.getLoggedInUser().getUserId()).map(this::details)
                .orElse(Map.of("status", "NONE"));
    }

    @Transactional
    public void review(String userId, boolean approve, String note) {
        EcommUser reviewer = auth.getLoggedInUser();
        if (!reviewer.isAdmin()) throw new AccessDeniedException("Admin access required");
        EcommUser user = users.findByUserIdForUpdate(userId).orElseThrow(() -> new com.mercato.ExceptionHandler.ResourceNotFoundException("User", "userId", userId));
        if (user.isAdmin()) throw new AccessDeniedException("Administrator accounts are read-only in user management");
        SellerApplication application = applications.findByUser_UserId(userId).orElseThrow(() -> new com.mercato.ExceptionHandler.ResourceNotFoundException("Seller application", "userId", userId));
        if (user.getSellerStatus() != SellerStatus.PENDING) throw new IllegalArgumentException("Only pending applications can be reviewed");
        if (approve && (!user.isEmailVerified() || !user.isEnabled() || user.isAccountLocked()))
            throw new IllegalArgumentException("The account must be active and email verified before approval");
        if (!approve && (note == null || note.isBlank())) throw new IllegalArgumentException("Give a reason for rejection");
        if (approve) {
            commissions.notifyNewSeller(user);
            user.getRoles().add(roles.findByRoleName(AppRole.ROLE_USER).orElseThrow());
            user.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());
        }
        user.setAccessVersion(user.getAccessVersion()+1);
        sessions.revokeAll(user.getUserId());
        user.setSellerStatus(approve ? SellerStatus.APPROVED : SellerStatus.REJECTED);
        application.setReviewedAt(Instant.now()); application.setReviewedBy(reviewer.getUserId()); application.setReviewNote(note);
        accessEvents.save(new AccountAccessEvent(user, reviewer, approve ? "SELLER_APPROVED" : "SELLER_REJECTED",
                note == null || note.isBlank() ? "Business verification confirmed in seller approval workflow." : note.strip().substring(0, Math.min(note.strip().length(), 500)),
                "Seller application pending", approve ? "Seller application approved" : "Seller application rejected"));
    }
}
