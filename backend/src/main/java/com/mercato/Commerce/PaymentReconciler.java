package com.mercato.Commerce;

import com.fasterxml.jackson.databind.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Jobs.*;
import com.mercato.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentReconciler {
    private final GatewayEventRepository inbox; private final GatewayAttemptRepository attempts;
    private final OrderRepository orders;private final RefundRepository refunds;private final ObjectMapper json;private final DurableJobs jobs;
    private final RefundObligations obligations;
    private final java.time.Clock clock;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    public PaymentReconciler(GatewayEventRepository inbox,GatewayAttemptRepository attempts,OrderRepository orders,RefundRepository refunds,ObjectMapper json,DurableJobs jobs,RefundObligations obligations,java.time.Clock clock){
        this.clock=clock;this.inbox=inbox;this.attempts=attempts;this.orders=orders;this.refunds=refunds;this.json=json;this.jobs=jobs;this.obligations=obligations;
    }
    /** Capture and its allocation job commit here, before any attempt to allocate inventory. */
    @Transactional
    public void process(String id) {
        var envelope=inbox.findById(id).orElseThrow(()->new PoisonJobException("Missing gateway inbox event"));
        JsonNode event;
        try{event=json.readTree(envelope.getEventJson());}catch(Exception e){throw new PoisonJobException("Invalid gateway inbox payload");}
        if("refund".equals(event.path("kind").asText())) {processRefund(event);envelope.setProcessedAt(clock.instant());return;}
        String orderId=event.path("orderId").asText();
        Order order=orders.lockByOrderId(orderId).orElseThrow(()->new TransientJobException("Awaiting committed order"));
        if(envelope.getProcessedAt()!=null)return;
        var payment=order.getPayment();if(payment==null)throw new TransientJobException("Awaiting committed payment");
        String attemptId="cashfree:"+event.path("identity").asText();
        GatewayAttempt attempt=attempts.findById(attemptId).orElseGet(GatewayAttempt::new);
        if(attempt.getOrderId()!=null && !attempt.getOrderId().equals(orderId)) {
            halt(payment,"CONFLICTING_PROVIDER_EVIDENCE");
            jobs.enqueue("CAPTURE_ATTENTION","conflicting-evidence:"+id,Map.of("orderId",orderId,"eventId",id));
            envelope.setProcessedAt(clock.instant());return;
        }
        String status=event.path("status").asText();
        boolean supported=Set.of("SUCCESS","FAILED","USER_DROPPED","PENDING","NOT_ATTEMPTED","CANCELLED").contains(status);
        attempt.setId(attemptId);attempt.setOrderId(orderId);attempt.setProvider("cashfree");attempt.setProviderPaymentId(event.path("identity").asText());
        if(!"SUCCESS".equals(attempt.getStatus()))attempt.setStatus(supported?status:"UNSUPPORTED");
        attempt.setUpdatedAt(clock.instant());
        attempt.setLatestEvidenceId(id);
        attempt.setEvidenceSource(event.path("source").asText("LEGACY_UNKNOWN"));
        attempt.setApiVersion(event.path("apiVersion").asText("UNSPECIFIED"));
        if (!supported) attention(order,attempt,id,"Unsupported provider payment status");
        else if (!"SUCCESS".equals(status) && PaymentEvidence.read(event,order.getTotalAmount(),order.getCurrency()).state()==PaymentEvidence.State.CONFLICTING)
            attention(order,attempt,id,"Payment status contradicts collection evidence");
        else if ("SUCCESS".equals(status)) {
            var facts = PaymentEvidence.read(event, order.getTotalAmount(), order.getCurrency());
            attempt.setEvidenceReason(facts.reason());
            switch (facts.state()) {
                case CONFLICTING -> attention(order, attempt, id, facts.reason());
                case INSUFFICIENT -> {
                    if (attempt.getCapturedAt() == null) attempt.setEvidenceState("INSUFFICIENT");
                    if (payment.getCapturedAt() == null) halt(payment,"INSUFFICIENT_PROVIDER_EVIDENCE");
                    // Per-observation key permits a later independent observation to request another GET.
                    jobs.enqueue("RECONCILE_ORDER","reconcile-evidence:" + id,Map.of("orderId",orderId));
                    if ("AUTHENTICATED_POLL".equals(event.path("source").asText()))
                        jobs.enqueue("CAPTURE_ATTENTION","insufficient-evidence:" + id,Map.of("orderId",orderId,"eventId",id));
                }
                case AUTHORIZED, RELEASED -> {
                    if (attempt.getCapturedAt() == null) {
                        attempt.setEvidenceState(facts.state().name());
                        if (!authorization(order,attempt,facts,id)) break;
                        if (payment.getCapturedAt() == null) {
                            payment.setStatus(PaymentStatus.AUTHORIZED);
                            order.setPaymentStatus(PaymentStatus.AUTHORIZED);
                            halt(payment,"AUTHORIZATION_REQUIRES_FINANCIAL_INTEGRATION");
                        }
                        jobs.enqueue("CAPTURE_ATTENTION","authorization-review:" + id,Map.of("orderId",orderId,"eventId",id));
                    }
                }
                case CAPTURED -> capture(order, attempt, facts, id, event.path("group").asText());
                case NONE -> { /* No new collection evidence. */ }
            }
        } else if(payment.getCapturedAt()==null && payment.getAuthorizedAt()==null && order.getOrderStatus()!=OrderStatus.CANCELLED) {
            PaymentStatus state=switch(status){case "FAILED"->PaymentStatus.FAILED;case "USER_DROPPED"->PaymentStatus.USER_DROPPED;default->PaymentStatus.PENDING;};
            payment.setStatus(state);order.setPaymentStatus(state);
        }
        attempts.save(attempt);envelope.setProcessedAt(clock.instant());
    }
    private boolean authorization(Order order,GatewayAttempt attempt,PaymentEvidence.Facts facts,String id) {
        var payment=order.getPayment();
        if ((attempt.getAuthorizationId()!=null && !attempt.getAuthorizationId().equals(facts.authorizationId()))
                || (attempt.getAuthorizedAt()!=null && !attempt.getAuthorizedAt().equals(facts.authorizedAt()))
                || (payment.getAuthorizationId()!=null && !payment.getAuthorizationId().equals(facts.authorizationId()))
                || (payment.getAuthorizedAt()!=null && !payment.getAuthorizedAt().equals(facts.authorizedAt()))) {
            attention(order,attempt,id,"Authorization identity or clock changed");return false;
        }
        attempt.setAuthorizationId(facts.authorizationId());
        attempt.setAuthorizedAmount(facts.originalAmount());
        attempt.setAuthorizedAt(facts.authorizedAt());
        if(attempt.getAuthorizationExpiresAt()==null || facts.expiresAt()!=null && facts.expiresAt().isBefore(attempt.getAuthorizationExpiresAt()))
            attempt.setAuthorizationExpiresAt(facts.expiresAt());
        // A different or changed hold must not replace an already identified authorization.
        if (payment.getAuthorizationId() == null) {
            payment.setAuthorizationId(facts.authorizationId());
            payment.setAuthorizedAmount(facts.originalAmount());
            payment.setAuthorizedAt(facts.authorizedAt());
            payment.setAuthorizationExpiresAt(facts.expiresAt());
        } else if(facts.expiresAt()!=null && (payment.getAuthorizationExpiresAt()==null || facts.expiresAt().isBefore(payment.getAuthorizationExpiresAt()))) {
            payment.setAuthorizationExpiresAt(facts.expiresAt());
        }
        return true;
    }
    private void capture(Order order,GatewayAttempt attempt,PaymentEvidence.Facts facts,String id,String group) {
        var payment = order.getPayment();
        if (!facts.authorizationId().isBlank() &&
                (attempt.getAuthorizationId()!=null && !attempt.getAuthorizationId().equals(facts.authorizationId())
                || payment.getAuthorizationId()!=null && !payment.getAuthorizationId().equals(facts.authorizationId()))) {
            attention(order,attempt,id,"Authorization identity changed during capture");return;
        }
        if (facts.authorizedAt()!=null && facts.expiresAt()!=null && !authorization(order,attempt,facts,id)) return;
        if (attempt.getCapturedAt() != null && attempt.getCapturedAmount() != null
                && attempt.getCapturedAmount().compareTo(facts.capturedAmount()) != 0) {
            attention(order,attempt,id,"Captured amount changed for proven attempt");return;
        }
        attempt.setAmount(facts.originalAmount());attempt.setCurrency(order.getCurrency());
        attempt.setCapturedAmount(facts.capturedAmount());
        Instant verifiedAt=clock.instant();
        if (attempt.getCaptureVerifiedAt()==null) attempt.setCaptureVerifiedAt(verifiedAt);
        if (attempt.getCapturedAt() == null) {
            attempt.setCapturedAt(verifiedAt);attempt.setCapturedAtProvenance("OBSERVED_VERIFICATION");
        } else if (attempt.getCapturedAtProvenance()==null) attempt.setCapturedAtProvenance("LEGACY_COMPATIBILITY");
        if (attempt.getProviderCapturedAt()==null) {
            attempt.setProviderCapturedAt(facts.capturedAt());attempt.setProviderCaptureTimeSource(facts.captureTimeSource());
        }
        if (attempt.getCaptureEvidenceId() == null) attempt.setCaptureEvidenceId(id);
        attempt.setEvidenceState("CAPTURED");
        if (payment.getCapturedAt() != null && !attempt.getProviderPaymentId().equals(payment.getGatewayReference())) {
            attention(order,attempt,id,"Extra verified capture requires reconciliation");return;
        }
        if (payment.getCapturedAt() != null && payment.getCapturedAmount()!=null && payment.getCapturedAmount().compareTo(facts.capturedAmount()) != 0) {
            attention(order,attempt,id,"Capture conflicts with preserved payment evidence");return;
        }
        if (payment.getCapturedAt() == null) {
            payment.setCapturedAt(attempt.getCapturedAt());payment.setCapturedAmount(facts.capturedAmount());
            payment.setCapturedAtProvenance("OBSERVED_VERIFICATION");
            payment.setCaptureEvidenceId(id);payment.setCompletedAt(verifiedAt);
            payment.setGatewayReference(attempt.getProviderPaymentId());payment.setPaymentMethod(method(group));
            payment.setStatus(PaymentStatus.SUCCESS);order.setPaymentStatus(PaymentStatus.SUCCESS);
        }
        if (payment.getCapturedAtProvenance()==null) payment.setCapturedAtProvenance("LEGACY_COMPATIBILITY");
        if (payment.getCapturedAmount()==null) payment.setCapturedAmount(facts.capturedAmount());
        if (payment.getCaptureVerifiedAt()==null) payment.setCaptureVerifiedAt(verifiedAt);
        if (payment.getProviderCapturedAt()==null) {
            payment.setProviderCapturedAt(facts.capturedAt());payment.setProviderCaptureTimeSource(facts.captureTimeSource());
        }
        if (payment.getCaptureEvidenceId()==null) payment.setCaptureEvidenceId(id);
        if (facts.capturedAmount().compareTo(order.getTotalAmount()) != 0) {
            halt(payment,"PARTIAL_CAPTURE_REQUIRES_UNIT_ATTRIBUTION");
            jobs.enqueue("CAPTURE_ATTENTION","partial-capture:" + id,Map.of("orderId",order.getOrderId(),"eventId",id));
            return;
        }
        if (Set.of("INSUFFICIENT_PROVIDER_EVIDENCE","AUTHORIZATION_REQUIRES_FINANCIAL_INTEGRATION","LEGACY_CAPTURE_REQUIRES_RECONCILIATION").contains(payment.getFinancialHaltReason()==null?"":payment.getFinancialHaltReason())) {
            payment.setFinancialHalt(false);payment.setFinancialHaltReason(null);
        }
        if (payment.isFinancialHalt()) return;
        if (order.getOrderStatus()==OrderStatus.CANCELLED) obligations.full(order,"Capture received after cancellation");
        else if (order.getAllocationCompletedAt()==null && !order.isAutomaticFullRefund())
            jobs.enqueue("ALLOCATE_ORDER","allocate:" + order.getOrderId(),Map.of("orderId",order.getOrderId()));
    }
    private void attention(Order order,GatewayAttempt attempt,String id,String reason) {
        if (attempt.getCapturedAt() == null) attempt.setEvidenceState("CONFLICTING");
        attempt.setEvidenceReason(reason);
        halt(order.getPayment(),"CONFLICTING_PROVIDER_EVIDENCE");
        jobs.enqueue("CAPTURE_ATTENTION","conflicting-evidence:" + id,Map.of("orderId",order.getOrderId(),"eventId",id));
    }
    private void halt(Payment payment,String reason) {
        // A weak/missing observation may never clear or weaken an existing explicit conflict.
        if (!"CONFLICTING_PROVIDER_EVIDENCE".equals(payment.getFinancialHaltReason())) {
            payment.setFinancialHalt(true);payment.setFinancialHaltReason(reason);
        }
    }
    private void processRefund(JsonNode event) {
        var existing=refunds.findByRefundId(event.path("identity").asText()).orElseThrow(()->new ManualInterventionJobException("Unknown merchant refund identity"));
        var order=orders.lockByOrderId(existing.getPayment().getOrder().getOrderId()).orElseThrow();
        var refund=refunds.findById(existing.getId()).orElseThrow();
        entityManager.refresh(refund);
        String statedOrder=event.path("orderId").asText();
        if(!statedOrder.isBlank()&&!statedOrder.equals(order.getOrderId()))throw new PoisonJobException("Refund order mismatch");
        if(event.has("amount")&&PaymentRules.decimal(event.path("amount").asText()).compareTo(refund.getAmount())!=0)throw new PoisonJobException("Refund amount mismatch");
        if(event.has("currency")&&!refund.getCurrency().equals(event.path("currency").asText()))throw new PoisonJobException("Refund currency mismatch");
        if(refund.getStatus()==RefundStatus.SUCCESS)return;
        if("SUCCESS".equals(event.path("status").asText())) {
            refund.setStatus(RefundStatus.SUCCESS);refund.setGatewayReference(event.path("providerReference").asText());
            obligations.completed(order,refund);
        } else if(Set.of("PENDING","ONHOLD").contains(event.path("status").asText())) refund.setStatus(RefundStatus.PENDING);
        else throw new ManualInterventionJobException("Refund failure requires classified gateway reconciliation");
    }
    private PaymentMethod method(String group) {
        return switch(group){case "credit_card","debit_card"->PaymentMethod.CARD;case "upi"->PaymentMethod.UPI;case "net_banking"->PaymentMethod.NET_BANKING;case "wallet"->PaymentMethod.WALLET;default->PaymentMethod.UNKNOWN;};
    }
}
