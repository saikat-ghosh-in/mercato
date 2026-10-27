package com.mercato.Commerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Jobs.*;
import com.mercato.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Service
public class RefundExecution implements JobHandler {
    private final RefundRepository refunds;private final OrderRepository orders;private final RefundObligations obligations;
    private final CashfreeGateway gateway;private final TransactionTemplate tx;
    public RefundExecution(RefundRepository refunds,OrderRepository orders,RefundObligations obligations,CashfreeGateway gateway,PlatformTransactionManager transactions){this.refunds=refunds;this.orders=orders;this.obligations=obligations;this.gateway=gateway;this.tx=new TransactionTemplate(transactions);}
    public String type(){return "REFUND_ORDER";}
    private record Request(String id,String orderId,BigDecimal amount,BigDecimal captured,String currency,RefundStatus status,String paymentReference,boolean halted){}
    public void handle(JobRecord job) {
        String id=(String)job.payload().get("refundId");
        Request request=tx.execute(t->{var r=refunds.findByRefundId(id).orElseThrow(()->new PoisonJobException("Missing refund obligation"));var order=orders.lockByOrderId(r.getPayment().getOrder().getOrderId()).orElseThrow();var p=order.getPayment();BigDecimal committed=refunds.findByPayment_IdOrderByIdAsc(p.getId()).stream().filter(Refund::committed).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);boolean halt=p.isFinancialHalt()||!com.mercato.Service.FulfillmentFunding.provenCollection(order)||p.getCapturedAt()==null||p.getCapturedAmount()==null||committed.compareTo(p.getCapturedAmount())>0;return new Request(id,order.getOrderId(),r.getAmount(),p.getCapturedAmount(),r.getCurrency(),r.getStatus(),r.getExpectedPaymentReference(),halt);});
        if(request.status()==RefundStatus.SUCCESS)return;
        if(request.halted()||request.paymentReference()==null||request.paymentReference().isBlank()||request.status()==RefundStatus.FAILED)throw new ManualInterventionJobException("Refund funding or primary-attempt ownership requires review");
        if(request.captured()==null || request.amount().signum()<=0 || request.amount().compareTo(request.captured())>0)throw new PoisonJobException("Refund exceeds verified captured funds");
        String path="/orders/"+request.orderId()+"/refunds";
        JsonNode result;
        try {
            // Always read the stable merchant identity first, including after a timeout/crash.
            try{result=gateway.get(path+"/"+id);}
            catch(CashfreeGateway.GatewayFailure e){if(!e.missing())throw e;result=gateway.post(path,Map.of("refund_id",id,"refund_amount",request.amount(),"refund_note","Mercato order refund","refund_speed","STANDARD"),"refund:"+id);}
        }catch(CashfreeGateway.GatewayFailure e){
            if(e.insufficientFunds()){
                attention(request,RefundStatus.ONHOLD,"WAITING_FOR_FUNDS");
                throw new WaitingJobException("REFUND_WAITING_FOR_FUNDS",Duration.ofHours(1));
            }
            if(e.temporary())throw new TransientJobException("Gateway outcome unknown; reconciliation required");
            attention(request,null,"NEEDS_ATTENTION");
            throw new ManualInterventionJobException("Refund rejected; unresolved customer obligation");
        }
        result=RefundEvidence.exact(result,id,request.paymentReference(),request.currency(),request.amount());
        String status=result.path("refund_status").asText();
        if("SUCCESS".equals(status)) {
            String providerId=result.path("cf_refund_id").asText();
            tx.executeWithoutResult(t->{var order=orders.lockByOrderId(request.orderId()).orElseThrow();var refund=refunds.findByRefundId(id).orElseThrow();if(refund.getStatus()!=RefundStatus.SUCCESS){refund.setStatus(RefundStatus.SUCCESS);refund.setFailureReason(null);refund.setGatewayReference(providerId);obligations.completed(order,refund);}});
        } else if(Set.of("PENDING","ONHOLD").contains(status)) {attention(request,RefundStatus.valueOf(status),null);throw new WaitingJobException("Refund pending at gateway",Duration.ofMinutes(2));}
        else throw new ManualInterventionJobException("Refund failed; investigate gateway eligibility and funds");
    }
    private void attention(Request request,RefundStatus status,String reason){
        tx.executeWithoutResult(t->{orders.lockByOrderId(request.orderId()).orElseThrow();var refund=refunds.findByRefundId(request.id()).orElseThrow();if(refund.getStatus()!=RefundStatus.SUCCESS){if(status!=null)refund.setStatus(status);refund.setFailureReason(reason);}});
    }
}
