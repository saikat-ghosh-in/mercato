package com.mercato.payment;

import com.mercato.Commerce.PaymentRules;
import com.mercato.Commerce.WebhookAuthenticator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;

class PaymentInvariantsTest {
    @Test void fullRefundUsesCapturedFundsAndSubtractsOnlyCommittedObligations() {
        assertEquals(new BigDecimal("873.47"), PaymentRules.refundable(new BigDecimal("999.47"), new BigDecimal("100.00"), new BigDecimal("26.00")));
        assertEquals(new BigDecimal("0.00"), PaymentRules.refundable(new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> PaymentRules.refundable(new BigDecimal("10"), new BigDecimal("11"), BigDecimal.ZERO));
    }
    @Test void rawBodyMustBeAuthenticatedBeforeParsingOrNormalizing() throws Exception {
        byte[] raw = "{\"amount\": 12.00,\"name\":\"म\"}\r\n".getBytes(StandardCharsets.UTF_8);
        String timestamp="1720000000123";
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        String signature=Base64.getEncoder().encodeToString(mac.doFinal(raw));
        assertTrue(WebhookAuthenticator.valid(raw, signature, timestamp, "secret"));
        assertFalse(WebhookAuthenticator.valid(new String(raw,StandardCharsets.UTF_8).trim().getBytes(StandardCharsets.UTF_8), signature,timestamp,"secret"));
        assertFalse(WebhookAuthenticator.valid(raw, "invalid",timestamp,"secret"));
        assertFalse(WebhookAuthenticator.valid(raw, signature,null,"secret"));
    }
    @Test void moneyRejectsOverprecisionAndNeverParsesViaDouble() {
        assertEquals(new BigDecimal("123456789.12"), PaymentRules.decimal("123456789.12"));
        assertThrows(IllegalArgumentException.class,()->PaymentRules.decimal("1.001"));
        assertThrows(IllegalArgumentException.class,()->PaymentRules.decimal("-1"));
    }
}
