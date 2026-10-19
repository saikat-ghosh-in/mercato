package com.mercato.Schedular;
import com.mercato.Repository.SellerNoticeRepository;
import com.mercato.Service.SellerNoticeMailer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
@Component @RequiredArgsConstructor
public class SellerNoticeJob {
    private final SellerNoticeRepository notices;
    private final SellerNoticeMailer mailer;
    @Scheduled(fixedDelayString = "${marketplace.notices.poll-ms:60000}", initialDelayString = "${marketplace.notices.initial-delay-ms:60000}")
    public void sendPending() {
        notices.findTop20ByEmailSentAtIsNullAndSupersededFalseAndNextAttemptAtLessThanEqualOrderByIdAsc(Instant.now())
                .forEach(notice -> mailer.send(notice.getId()));
    }
}
