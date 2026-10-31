package com.mercato.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mercato.Commerce.PaymentEvidence;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PaymentEvidenceTest {
    final ObjectMapper json = new ObjectMapper();
    ObjectNode event() {
        return json.createObjectNode().put("status","SUCCESS").put("orderId","ORD-1")
            .put("amount","52.00").put("currency","INR").put("source","AUTHENTICATED_POLL");
    }
    PaymentEvidence.Facts read(ObjectNode event) { return PaymentEvidence.read(event,new BigDecimal("52.00"),"INR"); }
    ObjectNode hold() {
        var e = event().put("isCaptured",false).put("authorizationId","verified-auth");
        e.putObject("authorization").put("start_time","2026-10-20T10:00:00+05:30")
            .put("end_time","2026-10-30T10:00:00+05:30");
        return e;
    }
    @Test void authorizationExpiresFromProviderTimeAndNeverReceiptTime() {
        var facts = read(hold());
        assertEquals(PaymentEvidence.State.AUTHORIZED,facts.state());
        assertEquals(Instant.parse("2026-10-20T04:30:00Z"),facts.authorizedAt());
        assertEquals(Instant.parse("2026-10-27T04:30:00Z"),facts.expiresAt());
        var e=hold();((ObjectNode)e.path("authorization")).put("end_time","2026-10-22T04:00:00Z");
        assertEquals(Instant.parse("2026-10-22T04:00:00Z"),read(e).expiresAt());
    }
    @Test void localOrMissingAuthorizationTimeCannotStartNewSevenDayClock() {
        var e=hold();((ObjectNode)e.path("authorization")).put("start_time","2026-10-20T10:00:00");
        assertEquals(PaymentEvidence.State.INSUFFICIENT,read(e).state());
        ((ObjectNode)e.path("authorization")).remove("start_time");
        assertNull(read(e).authorizedAt());assertNull(read(e).expiresAt());
    }
    @Test void authIdAndSuccessAreInsufficientWithoutAnExplicitCaptureFlag() {
        assertEquals(PaymentEvidence.State.INSUFFICIENT,read(event().put("authorizationId","ordinary-bank-auth")).state());
    }
    @Test void signedWebhookMustReconcileBeforeItCanEstablishCapture() {
        assertEquals(PaymentEvidence.State.INSUFFICIENT,read(event().put("isCaptured",true).put("source","SIGNED_WEBHOOK")).state());
        assertEquals(PaymentEvidence.State.CAPTURED,read(event().put("isCaptured",true)).state());
    }
    @Test void invalidExplicitFlagIsAConflict() {
        assertEquals(PaymentEvidence.State.CONFLICTING,read(event().put("isCaptured","true")).state());
    }
    @Test void contradictoryActionFlagAndAliasEvidenceCannotCapture() {
        var e=hold().put("isCaptured",true);
        ((ObjectNode)e.path("authorization")).put("action","VOID").put("status","SUCCESS");
        assertEquals(PaymentEvidence.State.CONFLICTING,read(e).state());
        assertEquals(PaymentEvidence.State.CONFLICTING,read(event().put("isCaptured",true).put("authorizationAliasConflict",true)).state());
        e=hold();((ObjectNode)e.path("authorization")).put("action","CAPTURE").put("status","SUCCESS").put("captured_amount","30.00");
        assertEquals(PaymentEvidence.State.CONFLICTING,read(e).state());
    }
    @Test void partialCaptureNeedsMatchingCompletedActionAndOriginalTerms() {
        var e=hold().put("isCaptured",true);
        ((ObjectNode)e.path("authorization")).put("captured_amount","30.00");
        assertEquals(PaymentEvidence.State.INSUFFICIENT,read(e).state());
        ((ObjectNode)e.path("authorization")).put("action","CAPTURE").put("status","SUCCESS");
        var facts=read(e);assertEquals(PaymentEvidence.State.CAPTURED,facts.state());
        assertEquals(new BigDecimal("30.00"),facts.capturedAmount());assertEquals(new BigDecimal("52.00"),facts.originalAmount());
        ((ObjectNode)e.path("authorization")).put("captured_amount","52.01");
        assertEquals(PaymentEvidence.State.CONFLICTING,read(e).state());
    }
    @Test void originalIdentityAmountAndCurrencyRemainIndependentFromCaptureAmount() {
        assertEquals(PaymentEvidence.State.CONFLICTING,read(event().put("isCaptured",true).put("amount","30.00")).state());
        assertEquals(PaymentEvidence.State.CONFLICTING,read(event().put("isCaptured",true).put("currency","USD")).state());
        assertEquals(PaymentEvidence.State.CONFLICTING,read(event().put("isCaptured",true).put("providerOrderId","other")).state());
    }
    @Test void nonSuccessStatusCannotAgreeWithAnExplicitCaptureClaim() {
        assertEquals(PaymentEvidence.State.CONFLICTING,read(event().put("status","FAILED").put("isCaptured",true)).state());
    }
    @Test void initiationTimeCannotBecomeProviderCaptureTime() {
        var e=event().put("isCaptured",true).put("paymentAt","2026-10-20T10:00:00Z");
        assertEquals(PaymentEvidence.State.CAPTURED,read(e).state());
        assertNull(read(e).capturedAt());
        e.put("paymentCompletedAt","2026-10-21T10:00:00");
        assertNull(read(e).capturedAt(),"An ambiguous local completion time is not a provider capture date");
    }
}
