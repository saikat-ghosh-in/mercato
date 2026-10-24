package com.mercato.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mercato.Commerce.*;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.ExceptionHandler.*;
import com.mercato.Jobs.DurableJobs;
import com.mercato.Payloads.Response.CashfreeOrderResponse;
import com.mercato.Repository.OrderRepository;
import com.mercato.Utils.AuthUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Browser operations and gateway transport. Capture/fulfillment are durable worker operations. */
@Service
public class CashfreeServiceImpl implements CashfreeService {
    private final CashfreeGateway gateway;private final PaymentInbox inbox;private final OrderRepository orders;
    private final AuthUtil auth;private final DurableJobs jobs;private final TransactionTemplate tx;
    private final String returnUrl,notifyUrl;
    public CashfreeServiceImpl(CashfreeGateway gateway,PaymentInbox inbox,OrderRepository orders,AuthUtil auth,DurableJobs jobs,PlatformTransactionManager transactions,@Value("${cashfree.return.url}")String returnUrl,@Value("${cashfree.notify.url}")String notifyUrl){this.gateway=gateway;this.inbox=inbox;this.orders=orders;this.auth=auth;this.jobs=jobs;this.tx=new TransactionTemplate(transactions);this.returnUrl=returnUrl;this.notifyUrl=notifyUrl;}
    public CashfreeOrderResponse createOrder(Order order,EcommUser user) {
        Map<String,Object> body=new LinkedHashMap<>();body.put("order_id",order.getOrderId());body.put("order_amount",order.getTotalAmount());body.put("order_currency",order.getCurrency());
        body.put("customer_details",Map.of("customer_id",user.getUserId(),"customer_name",user.getUsername(),"customer_email",user.getEmail(),"customer_phone",order.getRecipientPhone()));
        body.put("order_meta",Map.of("return_url",returnUrl+"?order_id="+order.getOrderId(),"notify_url",notifyUrl));
        var result=gateway.post("/orders",body,"create-order:"+order.getOrderId());
        return new CashfreeOrderResponse(result.path("cf_order_id").asText(),result.path("payment_session_id").asText());
    }
    @Transactional
    public void initiatePayment(Order order,PaymentMethod method,CashfreeOrderResponse result) {
        Payment payment=order.getPayment();
        if(payment==null){payment=new Payment();payment.setAmount(order.getTotalAmount());payment.setCurrency(order.getCurrency());payment.setGatewayName("cashfree");payment.setInitiatedAt(Instant.now());order.attachPayment(payment);}
        if(payment.getCapturedAt()!=null)throw new CustomBadRequestException("Order is already paid");
        payment.setPaymentMethod(method);payment.setStatus(PaymentStatus.INITIATED);payment.setCfOrderId(result.getCfOrderId());payment.setPaymentSessionId(result.getPaymentSessionId());order.setPaymentStatus(PaymentStatus.INITIATED);
    }
    @Transactional
    public void terminateOrder(String merchantOrderId) {
        jobs.enqueue("TERMINATE_ORDER","terminate:"+merchantOrderId,Map.of("orderId",merchantOrderId));
        // Reconciliation catches a capture racing with the termination request even if its webhook is delayed.
        jobs.enqueue("RECONCILE_ORDER","reconcile-cancel:"+merchantOrderId,Map.of("orderId",merchantOrderId));
    }
    public void handleWebhookEvent(String payload,String signature,String timestamp){inbox.receive(payload.getBytes(StandardCharsets.UTF_8),signature,timestamp);}
    public String retryPayment(Order order,EcommUser user) {
        if(order.getOrderStatus()!=OrderStatus.CREATED || order.getPayment().getCapturedAt()!=null)throw new CustomBadRequestException("Order is not payable");
        JsonNode state;
        try {state=gateway.get("/orders/"+order.getOrderId());}
        catch(CashfreeGateway.GatewayFailure failure){
            if(!failure.missing())throw failure;
            // A failed/ambiguous initial POST keeps its durable merchant order ID.
            // Recover that same checkout instead of creating an unrelated local order.
            var created=createOrder(order,user);
            return tx.execute(t->{var locked=orders.lockByOrderId(order.getOrderId()).orElseThrow();
                if(locked.getOrderStatus()!=OrderStatus.CREATED || locked.getPayment().getCapturedAt()!=null)throw new CustomBadRequestException("Order is not payable");
                initiatePayment(locked,PaymentMethod.UNKNOWN,created);return created.getPaymentSessionId();});
        }
        if(!"ACTIVE".equals(state.path("order_status").asText()))throw new CustomBadRequestException("Payment is no longer available for this order. Refresh its status.");
        String session=state.path("payment_session_id").asText();
        if(session.isBlank())throw new com.mercato.Jobs.TransientJobException("Gateway checkout session is not available yet");
        tx.executeWithoutResult(t->{var locked=orders.lockByOrderId(order.getOrderId()).orElseThrow();if(locked.getOrderStatus()!=OrderStatus.CREATED || locked.getPayment().getCapturedAt()!=null)throw new CustomBadRequestException("Order is not payable");locked.getPayment().setPaymentSessionId(session);});
        return session;
    }
    public String syncPaymentAndRefund(String orderId) {
        String email=auth.getLoggedInUser().getEmail();
        tx.executeWithoutResult(t->{orders.findByOrderIdAndCustomerEmail(orderId,email).orElseThrow(()->new ResourceNotFoundException("Order","orderId",orderId));jobs.enqueue("RECONCILE_ORDER","reconcile-browser:"+orderId+":"+(Instant.now().getEpochSecond()/10),Map.of("orderId",orderId));});
        return "Payment refresh requested";
    }
    public String initiateRefund(Order order,Refund refund){throw new IllegalStateException("Refunds must be persisted and executed through the durable refund handler");}
}
