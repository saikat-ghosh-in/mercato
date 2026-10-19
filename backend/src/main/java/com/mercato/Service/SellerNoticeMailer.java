package com.mercato.Service;

import com.mercato.Entity.SellerNotice;
import com.mercato.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor
public class SellerNoticeMailer {
    private final SellerNoticeRepository notices;
    private final CommissionSettingsRepository settings;
    private final CommissionService commissions;
    private final JavaMailSender mail;
    @Value("${mercato.email.from}") private String from;

    @Transactional
    public void send(Long id) {
        settings.lockSettings().orElseThrow();
        SellerNotice notice = notices.findById(id).orElseThrow();
        if (notice.getEmailSentAt() != null || (notice.getNextAttemptAt() != null && notice.getNextAttemptAt().isAfter(Instant.now()))) return;
        var change = notice.getChange();
        if ("ANNOUNCED".equals(notice.getEventType()) && "CANCELLED".equals(change.getStatus())) {
            // The separate cancellation notice supersedes an undelivered announcement.
            notice.setSuperseded(true); return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(notice.getEmail());
        boolean cancelled = "CANCELLED".equals(notice.getEventType());
        message.setSubject(cancelled ? "Mercato commission change cancelled" : "Mercato commission notice — at least 60 days before any change");
        message.setText(cancelled ? "The proposed Mercato commission of " + change.getRate() + "% has been cancelled. Your current rate is unchanged. See Seller Dashboard → Notices for details."
                : "Mercato proposes changing commission from " + change.getPreviousRate() + "% to " + change.getRate() + "%.\n\nReason: " + change.getReason()
                + "\n\nThis change will not take effect until at least 60 full days after all required email notices have been accepted by the mail service. The final effective date and status are available in Seller Dashboard → Notices. Delayed notices extend the notice period. Existing orders keep their captured rate.\n\nYou may stop listing new products before the effective date. Contact Mercato through marketplace Help for questions.");
        notice.setAttempts(notice.getAttempts() + 1);
        try {
            mail.send(message);
            notice.setEmailSentAt(Instant.now());
            commissions.finishNotifying(change);
        } catch (org.springframework.mail.MailException exception) {
            notice.setNextAttemptAt(Instant.now().plusSeconds(Math.min(21600, 60L * (1L << Math.min(notice.getAttempts(), 8)))));
        }
    }
}
