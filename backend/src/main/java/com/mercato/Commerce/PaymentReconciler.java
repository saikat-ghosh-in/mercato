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
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    public PaymentReconciler(GatewayEventRepository inbox,GatewayAttemptRepository attempts,OrderRepository orders,RefundRepository refunds,ObjectMapper json,DurableJobs jobs,RefundObligations obligations){
        this.inbox=inbox;this.attempts=attempts;this.orders=orders;this.refunds=refunds;this.json=json;this.jobs=jobs;this.obligations=obligations;
    }
    /** Capture and its allocation job commit here, before any attempt to allocate inventory. */
    @Transactional
    public void process(String id) {
        var envelope=inbox.findById(id).orElseThrow(()->new PoisonJobException("Missing gateway inbox event"));
        JsonNode event;
        try{event=json.readTree(envelope.getEventJson());}catch(Exception e){throw new PoisonJobException("Invalid gateway inbox payload");}
        if("refund".equals(event.path("kind").asText())) {processRefund(event);envelope.setProcessedAt(Instant.now());return;}
        String orderId=event.path("orderId").asText();
        Order order=orders.lockByOrderId(orderId).orElseThrow(()->new TransientJobException("Awaiting committed order"));
        if(envelope.getProcessedAt()!=null)return;
        var payment=order.getPayment();if(payment==null)throw new TransientJobException("Awaiting committed payment");
        String attemptId="cashfree:"+event.path("identity").asText();
        GatewayAttempt attempt=attempts.findById(attemptId).orElseGet(GatewayAttempt::new);
        if(attempt.getOrderId()!=null && !attempt.getOrderId().equals(orderId))throw new PoisonJobException("Gateway attempt belongs to another order");
        String status=event.path("status").asText();
        if(!Set.of("SUCCESS","FAILED","USER_DROPPED","PENDING","NOT_ATTEMPTED","CANCELLED").contains(status)) throw new PoisonJobException("Unsupported payment state");
        attempt.setId(attemptId);attempt.setOrderId(orderId);attempt.setProvider("cashfree");attempt.setProviderPaymentId(event.path("identity").asText());
        if(!"SUCCESS".equals(attempt.getStatus()))attempt.setStatus(status);
        attempt.setUpdatedAt(Instant.now());
        if("SUCCESS".equals(status)) {
            BigDecimal amount=verifiedAmount(event,order);
            attempt.setAmount(amount);attempt.setCurrency(order.getCurrency());
            if(attempt.getCapturedAt()==null)attempt.setCapturedAt(Instant.now());
            if(payment.getCapturedAt()!=null && !attempt.getProviderPaymentId().equals(payment.getGatewayReference())) {
                // Preserve the extra verified capture evidence before the separate attention job.
                attempts.save(attempt);envelope.setProcessedAt(Instant.now());
                jobs.enqueue("CAPTURE_ATTENTION","extra-capture:"+attemptId,Map.of("orderId",orderId,"attemptId",attemptId));return;
            }
            if(payment.getCapturedAt()==null) {
                payment.setCapturedAt(attempt.getCapturedAt());payment.setCapturedAmount(amount);payment.setCompletedAt(attempt.getCapturedAt());
                payment.setGatewayReference(attempt.getProviderPaymentId());payment.setPaymentMethod(method(event.path("group").asText()));
                payment.setStatus(PaymentStatus.SUCCESS);order.setPaymentStatus(PaymentStatus.SUCCESS);
            }
            if(order.getOrderStatus()==OrderStatus.CANCELLED)obligations.full(order,"Capture received after cancellation");
            else if(order.getAllocationCompletedAt()==null && !order.isAutomaticFullRefund())
                jobs.enqueue("ALLOCATE_ORDER","allocate:"+orderId,Map.of("orderId",orderId));
        } else if(payment.getCapturedAt()==null && order.getOrderStatus()!=OrderStatus.CANCELLED) {
            PaymentStatus state=switch(status){case "FAILED"->PaymentStatus.FAILED;case "USER_DROPPED"->PaymentStatus.USER_DROPPED;default->PaymentStatus.PENDING;};
            payment.setStatus(state);order.setPaymentStatus(state);
        }
        attempts.save(attempt);envelope.setProcessedAt(Instant.now());
    }
    private BigDecimal verifiedAmount(JsonNode event,Order order) {
        try {
            BigDecimal amount=PaymentRules.decimal(event.path("amount").asText());
            if(amount.compareTo(order.getTotalAmount())!=0 || !order.getCurrency().equals(event.path("currency").asText()))
                throw new PoisonJobException("Captured amount or currency does not match order");
            return amount;
        }catch(IllegalArgumentException e){throw new PoisonJobException("Missing or invalid captured amount");}
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
