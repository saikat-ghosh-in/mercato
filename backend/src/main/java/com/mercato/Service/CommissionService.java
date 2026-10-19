package com.mercato.Service;

import com.mercato.Entity.*;
import com.mercato.Repository.*;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class CommissionService {
    public static final int NOTICE_DAYS = 60;
    private final CommissionSettingsRepository settings;
    private final CommissionChangeRepository changes;
    private final SellerNoticeRepository notices;
    private final UserRepository users;
    private final AuthUtil auth;

    @Transactional
    public BigDecimal currentRate() {
        CommissionSettings current = settings.lockSettings().orElseThrow();
        for (CommissionChange change : changes.findByStatusIn(List.of("SCHEDULED"))) {
            if (change.getEffectiveAt() != null && !Instant.now().isBefore(change.getEffectiveAt())) {
                current.setRate(change.getRate()); change.setStatus("APPLIED");
            }
        }
        return current.getRate();
    }

    public static void validateChange(BigDecimal current, BigDecimal proposed, String reason, String confirmation) {
        if (proposed == null || proposed.scale() > 2 || proposed.signum() < 0 || proposed.compareTo(new BigDecimal("20")) > 0)
            throw new IllegalArgumentException("Commission must be 0–20%, with at most two decimal places");
        if (proposed.compareTo(current) == 0 || proposed.subtract(current).abs().compareTo(new BigDecimal("5")) > 0)
            throw new IllegalArgumentException("Use a different rate, within 5 percentage points of the current rate");
        if (reason == null || reason.trim().length() < 20 || reason.length() > 1000)
            throw new IllegalArgumentException("Explain the change in 20–1000 characters");
        if (!("SCHEDULE " + proposed.stripTrailingZeros().toPlainString() + "%").equals(confirmation))
            throw new IllegalArgumentException("Type the exact scheduling confirmation");
    }

    @Transactional
    public void schedule(BigDecimal rate, String reason, String confirmation) {
        EcommUser actor = auth.getLoggedInUser();
        if (!actor.isAdmin()) throw new AccessDeniedException("Admin access required");
        BigDecimal current = currentRate();
        validateChange(current, rate, reason, confirmation);
        if (!pending().isEmpty()) throw new IllegalArgumentException("Cancel the existing scheduled change before creating another");
        CommissionChange change = new CommissionChange();
        change.setPreviousRate(current); change.setRate(rate); change.setReason(reason.trim());
        change.setCreatedAt(Instant.now()); change.setCreatedBy(actor.getUserId());
        changes.saveAndFlush(change);
        for (EcommUser seller : users.findUsersByRoleNames(List.of(AppRole.ROLE_SELLER))) {
            if (seller.getSellerStatus() == null || seller.getSellerStatus() == SellerStatus.APPROVED)
                addNotice(seller, change, "ANNOUNCED");
        }
        finishNotifying(change);
    }

    private List<CommissionChange> pending() { return changes.findByStatusIn(List.of("NOTIFYING", "SCHEDULED")); }

    private void addNotice(EcommUser seller, CommissionChange change, String event) {
        if (notices.existsByUserIdAndChange_IdAndEventType(seller.getUserId(), change.getId(), event)) return;
        SellerNotice notice = new SellerNotice(); notice.setUserId(seller.getUserId()); notice.setEmail(seller.getEmail());
        notice.setChange(change); notice.setEventType(event); notice.setCreatedAt(Instant.now()); notice.setNextAttemptAt(Instant.now()); notices.saveAndFlush(notice);
    }

    @Transactional
    public void notifyNewSeller(EcommUser seller) {
        currentRate();
        for (CommissionChange change : pending()) {
            addNotice(seller, change, "ANNOUNCED"); change.setStatus("NOTIFYING"); change.setEffectiveAt(null);
        }
    }

    @Transactional
    public void finishNotifying(CommissionChange change) {
        if (!"NOTIFYING".equals(change.getStatus())) return;
        List<SellerNotice> sent = notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED");
        if (sent.stream().anyMatch(notice -> notice.getEmailSentAt() == null)) return;
        Instant completed = sent.stream().map(SellerNotice::getEmailSentAt).max(Comparator.naturalOrder()).orElse(Instant.now());
        change.setEffectiveAt(completed.plus(Duration.ofDays(NOTICE_DAYS))); change.setStatus("SCHEDULED");
    }

    @Transactional
    public void cancel(Long id) {
        if (!auth.getLoggedInUser().isAdmin()) throw new AccessDeniedException("Admin access required");
        currentRate();
        CommissionChange change = changes.findById(id).orElseThrow(() -> new com.mercato.ExceptionHandler.ResourceNotFoundException("Commission change", "id", String.valueOf(id)));
        if (!List.of("NOTIFYING", "SCHEDULED").contains(change.getStatus())) throw new IllegalArgumentException("Only a future change can be cancelled");
        change.setStatus("CANCELLED");
        for (SellerNotice original : notices.findByChange_IdAndEventType(id, "ANNOUNCED")) {
            users.findByUserId(original.getUserId()).ifPresent(user -> addNotice(user, change, "CANCELLED"));
        }
    }

    @Transactional
    public Map<String, Object> policy() {
        BigDecimal rate = currentRate();
        List<Map<String, Object>> history = changes.findAllByOrderByCreatedAtDesc().stream().map(change -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", change.getId()); data.put("previousRate", change.getPreviousRate()); data.put("rate", change.getRate());
            data.put("status", change.getStatus()); data.put("reason", change.getReason()); data.put("createdAt", change.getCreatedAt());
            data.put("effectiveAt", change.getEffectiveAt());
            data.put("createdBy", change.getCreatedBy());
            data.put("emailsPending", notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED").stream().filter(n -> n.getEmailSentAt() == null).count());
            return data;
        }).toList();
        return Map.of("rate", rate, "noticeDays", NOTICE_DAYS, "changes", history);
    }

    @Transactional
    public List<Map<String, Object>> myNotices() {
        currentRate();
        return notices.findByUserIdOrderByCreatedAtDesc(auth.getLoggedInUser().getUserId()).stream().map(notice -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", notice.getId()); dto.put("event", notice.getEventType()); dto.put("read", notice.getReadAt() != null);
            dto.put("createdAt", notice.getCreatedAt()); dto.put("rate", notice.getChange().getRate());
            dto.put("previousRate", notice.getChange().getPreviousRate()); dto.put("effectiveAt", notice.getChange().getEffectiveAt());
            dto.put("status", notice.getChange().getStatus()); dto.put("reason", notice.getChange().getReason());
            dto.put("emailSent", notice.getEmailSentAt() != null); return dto;
        }).toList();
    }

    @Transactional public void markRead(Long id) {
        SellerNotice notice = notices.findByIdAndUserId(id, auth.getLoggedInUser().getUserId())
                .orElseThrow(() -> new com.mercato.ExceptionHandler.ResourceNotFoundException("Notice", "id", String.valueOf(id)));
        notice.setReadAt(Instant.now());
    }
}
