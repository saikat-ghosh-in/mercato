package com.mercato.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Mapper.PaymentMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PaymentPresentationTest {
    @Test void incompleteFundingFactsAreUnverifiedWithoutThrowing() {
        var order=new com.mercato.Entity.fulfillment.Order();var payment=new Payment();order.setPayment(payment);
        payment.setCapturedAt(Instant.parse("2026-10-20T10:00:00Z"));payment.setCapturedAmount(new BigDecimal("52.00"));
        var funding=new com.mercato.Service.FulfillmentFunding();
        assertFalse(assertDoesNotThrow(()->funding.verified(order)),"A missing immutable order total cannot grant funding");
        order.setTotalAmount(BigDecimal.ZERO);assertFalse(funding.verified(order));
        order.setTotalAmount(new BigDecimal("-1.00"));assertFalse(funding.verified(order));
        assertFalse(funding.verified(null));
    }
    @Test void ownerResponseSeparatesOriginalAuthorizationActualCaptureAndFundingHalt() {
        var payment=new Payment();payment.setAmount(new BigDecimal("52.00"));payment.setCurrency("INR");
        payment.setPaymentMethod(PaymentMethod.CARD);payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAuthorizedAmount(new BigDecimal("52.00"));payment.setAuthorizedAt(Instant.parse("2026-10-20T10:00:00Z"));
        payment.setAuthorizationExpiresAt(Instant.parse("2026-10-27T10:00:00Z"));
        payment.setCapturedAmount(new BigDecimal("32.00"));payment.setCapturedAt(Instant.parse("2026-10-21T10:00:00Z"));
        payment.setFinancialHalt(true);payment.setFinancialHaltReason("PARTIAL_CAPTURE_REQUIRES_UNIT_ATTRIBUTION");
        var value=new ObjectMapper().findAndRegisterModules().valueToTree(PaymentMapper.toDTO(payment));
        assertEquals(0,new BigDecimal("32.00").compareTo(value.path("capturedAmount").decimalValue()));
        assertEquals(0,new BigDecimal("52.00").compareTo(value.path("authorizedAmount").decimalValue()));
        assertEquals("CAPTURE_ON_AUTH",value.path("paymentPolicy").asText());
        assertEquals(1,value.path("paymentPolicyVersion").asInt());
        assertTrue(value.path("financialHalt").asBoolean());
        assertTrue(value.hasNonNull("authorizedAt"));assertTrue(value.hasNonNull("authorizationExpiresAt"));
    }
}
