package com.mercato.mail;

import com.mercato.Mail.MailFailure;
import jakarta.mail.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class MailFailureTest {
    @Test void unauthorizedIpNeedsOperatorAttentionWithoutLeakingProviderText() {
        var result=MailFailure.classify(new MailAuthenticationException(new AuthenticationFailedException("525 5.7.1 Unauthorized IP address secret=private")));
        assertEquals("SMTP_IP_NOT_AUTHORIZED",result.code());
        assertEquals(525,result.smtpCode());assertFalse(result.retryable());
        assertFalse(result.message().contains("private"));
    }
    @Test void credentialsAndRejectedRecipientsAreNotRetriedForever() {
        assertFalse(MailFailure.classify(new MailAuthenticationException("535 invalid credentials password=secret")).retryable());
        var failure=MailFailure.classify(new MailSendException("550 5.1.1 recipient rejected"));
        assertEquals("SMTP_REJECTED",failure.code());assertFalse(failure.retryable());
    }
    @Test void timeoutsAndTemporaryProviderFailuresRetryWithoutClaimingDelivery() {
        assertTrue(MailFailure.classify(new MailSendException("connection failed",new SocketTimeoutException())).retryable());
        assertTrue(MailFailure.classify(new MailSendException("451 temporary provider error")).retryable());
        assertTrue(MailFailure.classify(new MailAuthenticationException("454 4.7.0 temporary authentication failure")).retryable());
    }
}
