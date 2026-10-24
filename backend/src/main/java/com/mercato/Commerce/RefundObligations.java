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
    @Transactional
    public void full(Order order,String reason) {
        order.setAutomaticFullRefund(true);
        for(var line:order.getOrderLines())if(line.getPendingQty()>0){line.cancel(line.getPendingQty());line.setOrderLineStatus(line.deriveStatus());}
        order.setOrderStatus(OrderStatus.CANCELLED);
        request(order,true,reason);
    }
    @Transactional
    public void allocationFailed(String orderId) {
        var order=orders.lockByOrderId(orderId).orElseThrow();
        order.setInventoryFinalizationFailed(true);full(order,"Paid items unavailable");
    }
    @Transactional
    public void cancelled(Order order) {
        if(order.getPayment()==null || order.getPayment().getCapturedAt()==null)return;
        boolean full=order.getOrderLines().stream().allMatch(l->l.getCancelledQty()==l.getOrderedQty());
        request(order,full,"Order cancellation");
    }
    private void request(Order order,boolean full,String reason) {
        var payment=order.getPayment();
        if(payment.getCapturedAmount()==null)throw new ManualInterventionJobException("Capture evidence required before refund");
        var history=refunds.findByPayment_IdOrderByIdAsc(payment.getId());
        BigDecimal committed=history.stream().filter(r->r.getStatus()!=RefundStatus.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal target=full?payment.getCapturedAmount():order.getOrderLines().stream().map(l->l.getUnitPrice().multiply(BigDecimal.valueOf(l.getCancelledQty()))).reduce(BigDecimal.ZERO,BigDecimal::add).min(payment.getCapturedAmount());
        BigDecimal amount=target.subtract(committed);
        if(amount.signum()<=0)return;
        PaymentRules.refundable(payment.getCapturedAmount(),committed,amount);
        String key=order.getOrderId()+":"+target.toPlainString();
        String refundId="RFD-"+UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
        if(refunds.findByRefundId(refundId).isPresent())return;
        var refund=Refund.builder().refundId(refundId).payment(payment).amount(amount).currency(payment.getCurrency()).status(RefundStatus.PENDING).reason(reason).build();
        refunds.saveAndFlush(refund);payment.getRefunds().add(refund);
        order.setPaymentStatus(PaymentStatus.REFUND_INITIATED);
        jobs.enqueue("REFUND_ORDER","refund:"+refundId,Map.of("refundId",refundId));
        notify(order,refund,false);
    }
    public void completed(Order order,Refund refund) {
        BigDecimal total=refunds.findByPayment_IdOrderByIdAsc(order.getPayment().getId()).stream().filter(r->r.getStatus()==RefundStatus.SUCCESS).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        var state=total.compareTo(order.getPayment().getCapturedAmount())>=0?PaymentStatus.REFUNDED:PaymentStatus.PARTIALLY_REFUNDED;
        order.setPaymentStatus(state);order.getPayment().setStatus(state);notify(order,refund,true);
    }
    private void notify(Order order,Refund refund,boolean done) {
        String phase=done?"completed":"initiated";
        jobs.enqueue("AUTH_EMAIL","refund-notice:"+refund.getRefundId()+":"+phase+":customer",Map.of("to",order.getCustomerEmail(),"subject","Mercato refund "+phase,"text","Your refund of ₹"+refund.getAmount()+" for order "+order.getOrderId()+" has been "+phase+". Reference: "+refund.getRefundId()+". You can follow its status in Your orders."));
        var sellers=new TreeSet<String>();order.getOrderLines().forEach(l->{if(l.getSellerEmail()!=null)sellers.add(l.getSellerEmail());});
        for(String email:sellers)jobs.enqueue("AUTH_EMAIL","refund-notice:"+refund.getRefundId()+":"+phase+":seller:"+email,Map.of("to",email,"subject","Mercato order refund "+phase,"text","A refund affecting your items in fulfillment "+order.getOrderLines().stream().filter(l->email.equals(l.getSellerEmail())).findFirst().orElseThrow().getFulfillmentId()+" has been "+phase+". See Seller Dashboard for the updated earnings and item status."));
    }
}
