package com.mercato.Commerce;

import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Jobs.*;
import com.mercato.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class RefundObligations {
    private final RefundRepository refunds;private final OrderRepository orders;private final DurableJobs jobs;
    public RefundObligations(RefundRepository refunds,OrderRepository orders,DurableJobs jobs){this.refunds=refunds;this.orders=orders;this.jobs=jobs;}
    @Transactional public void full(Order order,String reason) {
        order.setAutomaticFullRefund(true);
        boolean changed=false;
        for(var line:order.getOrderLines())if(line.getPendingQty()>0){
            if(UnitFinancials.attributed(line))UnitFinancials.transition(line,OrderLineAction.CANCEL,line.getPendingQty(),TransitionTrigger.ADMIN,"automatic-full",java.time.Instant.now());
            line.cancel(line.getPendingQty());line.setOrderLineStatus(line.deriveStatus());changed=true;
        }
        if(changed)order.workflowChanged();order.setOrderStatus(OrderStatus.CANCELLED);request(order,true,reason);
    }
    @Transactional public void allocationFailed(String id){var o=orders.lockByOrderId(id).orElseThrow();o.setInventoryFinalizationFailed(true);full(o,"Paid items unavailable");}
    @Transactional public void cancelled(Order order){
        if(order.getPayment()==null || order.getPayment().getCapturedAt()==null)return;
        request(order,UnitFinancials.closed(order)||order.getOrderLines().stream().allMatch(l->l.getCancelledQty()==l.getOrderedQty()),"Order cancellation");
    }
    /** Caller established physical receipt/approval and recorded its guarded command in this transaction. */
    @Transactional public void approvedReturn(Order order){request(order,UnitFinancials.closed(order),"Approved received return");}
    private void attention(Order order,String reason){jobs.enqueue("CAPTURE_ATTENTION","refund-attribution:"+order.getOrderId()+":"+order.getWorkflowRevision()+":"+UUID.nameUUIDFromBytes(reason.getBytes(StandardCharsets.UTF_8)),Map.of("orderId",order.getOrderId(),"reason",reason));}
    private void request(Order order,boolean full,String reason){
        var payment=order.getPayment();
        if(payment==null || payment.getCapturedAt()==null || payment.getCapturedAmount()==null || payment.getCapturedAmount().signum()<=0)return;
        if(payment.isFinancialHalt()||!com.mercato.Service.FulfillmentFunding.provenCollection(order)){attention(order,"Funding evidence requires review before refund");return;}
        var history=refunds.findByPayment_IdOrderByIdAsc(payment.getId());
        long captured=OrderMoneySnapshot.paise(payment.getCapturedAmount());
        long committed=history.stream().filter(Refund::committed).mapToLong(r->OrderMoneySnapshot.paise(r.getAmount())).sum();
        if(committed>captured){attention(order,"Refunds exceed verified capture");return;}
        var allocations=new ArrayList<RefundUnitAllocation>();
        if(UnitFinancials.attributed(order)){
            // An old/unattributed refund cannot be assigned to today's units after the fact.
            if(history.stream().anyMatch(r->r.committed() && r.getAllocations().stream().mapToLong(RefundUnitAllocation::totalPaise).sum()!=OrderMoneySnapshot.paise(r.getAmount()))){attention(order,"Existing refund has no reliable unit attribution");return;}
            for(var unit:UnitFinancials.units(order)){
                String stage=unit.getState().getStage();if(!Set.of("CANCELLED","RETURNED").contains(stage))continue;
                boolean allCharges=full || stage.equals("RETURNED") || !"CUSTOMER_REQUEST".equals(unit.getState().getCause());
                long m=unit.getNetMerchandisePaise(),d=unit.getBuyerNormalDeliveryPaise()+unit.getBuyerExpeditedDeliveryPaise(),p=allCharges?unit.getPlatformPaise():0,h=allCharges?unit.getHandlingPaise():0;
                for(var r:history)if(r.committed())for(var a:r.getAllocations())if(a.getUnit().getUnitId().equals(unit.getUnitId())){m-=a.getMerchandisePaise();d-=a.getDeliveryPaise();p-=a.getPlatformPaise();h-=a.getHandlingPaise();}
                if(m<0||d<0||p<0||h<0){attention(order,"Refund component exceeds immutable unit");return;}
                if(m+d+p+h>0){var a=new RefundUnitAllocation();a.setUnit(unit);a.setMerchandisePaise(m);a.setDeliveryPaise(d);a.setPlatformPaise(p);a.setHandlingPaise(h);a.setCause(full?"FULL_CLOSURE":unit.getState().getCause());allocations.add(a);}
            }
        } else if(!full){attention(order,"Legacy partial refund needs policy/unit review");return;}
        long amount=allocations.stream().mapToLong(RefundUnitAllocation::totalPaise).sum();
        if(!UnitFinancials.attributed(order))amount=captured-committed; // Verified full closure needs no invented historical split.
        if(amount<=0)return;
        if(amount>captured-committed){attention(order,"Requested units are not covered by verified capture");return;}
        long target=committed+amount;
        String key=order.getOrderId()+":"+target;
        String id="RFD-"+UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
        if(refunds.findByRefundId(id).isPresent()){attention(order,"Existing failed obligation requires reconciliation");return;}
        var refund=Refund.builder().refundId(id).payment(payment).amount(BigDecimal.valueOf(amount,2)).currency(payment.getCurrency()).status(RefundStatus.PENDING).reason(reason).expectedPaymentReference(payment.getGatewayReference()).build();
        for(var a:allocations){a.setId(id+":"+a.getUnit().getUnitId());a.setRefund(refund);refund.getAllocations().add(a);}
        refunds.saveAndFlush(refund);payment.getRefunds().add(refund);order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        jobs.enqueue("REFUND_ORDER","refund:"+id,Map.of("refundId",id));notify(order,refund,false);
    }
    public void completed(Order order,Refund refund){
        BigDecimal total=refunds.findByPayment_IdOrderByIdAsc(order.getPayment().getId()).stream().filter(r->r.getStatus()==RefundStatus.SUCCESS).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        var state=total.compareTo(order.getPayment().getCapturedAmount())>=0?PaymentStatus.REFUNDED:PaymentStatus.PARTIALLY_REFUNDED;
        order.setPaymentStatus(state);order.getPayment().setStatus(state);notify(order,refund,true);
    }
    private void notify(Order order,Refund refund,boolean done){
        String phase=done?"completed":"initiated";
        jobs.enqueue("AUTH_EMAIL","refund-notice:"+refund.getRefundId()+":"+phase+":customer",Map.of("to",order.getCustomerEmail(),"subject","Mercato refund "+phase,"text","Your refund of ₹"+refund.getAmount()+" for order "+order.getOrderId()+" has been "+phase+". Reference: "+refund.getRefundId()+". You can follow its status in Your orders."));
        var affected=new TreeMap<String,String>();
        for(var a:refund.getAllocations()){var l=a.getUnit().getOrderLine();if(l.getSellerEmail()!=null)affected.put(l.getSellerEmail(),l.getFulfillmentId());}
        // A full legacy obligation affects every line; no buyer amount or other seller identity is disclosed.
        if(refund.getAllocations().isEmpty())for(var l:order.getOrderLines())if(l.getSellerEmail()!=null)affected.put(l.getSellerEmail(),l.getFulfillmentId());
        affected.forEach((email,fulfillment)->jobs.enqueue("AUTH_EMAIL","refund-notice:"+refund.getRefundId()+":"+phase+":seller:"+email,Map.of("to",email,"subject","Mercato order refund "+phase,"text","A refund affecting your items in fulfillment "+fulfillment+" has been "+phase+". See Seller Dashboard for item status and earnings.")));
    }
}
