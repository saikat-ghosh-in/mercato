package com.mercato.Commerce;
import com.mercato.Repository.OrderRepository;
import com.mercato.Utils.AuthUtil;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Entity.fulfillment.OrderStatus;
import com.mercato.Mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class PaymentSessionRecovery {
    private final OrderRepository orders;private final AuthUtil auth;
    public PaymentSessionRecovery(OrderRepository orders,AuthUtil auth){this.orders=orders;this.auth=auth;}
    @Transactional(readOnly=true)
    public Object recoverIntent(String operation){
        if(operation==null||operation.isBlank()||operation.length()>120)throw new IllegalArgumentException("A valid checkout operationId is required");
        String buyer=auth.getLoggedInUser().getUserId();
        var rows=orders.checkoutIntent(buyer,operation);
        if(rows.isEmpty())throw new ResourceNotFoundException("Checkout intent","operationId",operation);
        var order=orders.findByOrderId((String)rows.get(0)[0]).filter(o->buyer.equals(o.getCustomerUserId())).orElseThrow(()->new ResourceNotFoundException("Checkout intent","operationId",operation));
        var request=new com.mercato.Payloads.Request.OrderCaptureRequestDTO();
        try{
            var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(order.getCheckoutRequestBody());
            if(!body.isArray()||body.size()!=4)throw new IllegalStateException("Invalid stored checkout intent");
            request.setAddressId(body.get(0).asText());request.setExpectedTotal(body.get(1).isNull()?null:new java.math.BigDecimal(body.get(1).asText()));
            request.setDeliveryMethod(body.get(2).isNull()?null:com.mercato.Entity.cart.DeliveryMethod.valueOf(body.get(2).asText()));request.setExpectedCartVersion(body.get(3).longValue());request.setOperationId(operation);
        }catch(java.io.IOException e){throw new IllegalStateException("Stored checkout intent is unreadable",e);}
        var payment=order.getPayment();
        boolean payable=CheckoutPayability.payable(order);
        String session=payable?payment.getPaymentSessionId():null;
        var out=new LinkedHashMap<String,Object>();out.put("operationId",operation);out.put("orderId",order.getOrderId());out.put("request",request);out.put("state",payable?(session==null||session.isBlank()?"REPLAY_CAPTURE":"PAYMENT_READY"):"ORDER_UPDATED");out.put("canReplayCapture",payable);out.put("paymentSessionId",null);out.put("order",OrderMapper.toDTO(order));return out;
    }
    @Transactional(readOnly=true)
    public Object recover(String id){
        return session(id,null);
    }
    /** Only the protected preparation command supplies a freshly guarded session. */
    @Transactional(readOnly=true)
    public Object prepared(String id,String preparedSession){
        return session(id,preparedSession);
    }
    private Object session(String id,String preparedSession){
        var order=orders.findByOrderIdAndCustomerEmail(id,auth.getLoggedInUser().getEmail()).orElseThrow(()->new ResourceNotFoundException("Order","orderId",id));
        var payment=order.getPayment();boolean canPrepare=CheckoutPayability.payable(order);
        boolean canResume=canPrepare && preparedSession!=null && !preparedSession.isBlank() && preparedSession.equals(payment.getPaymentSessionId());
        var result=new LinkedHashMap<String,Object>();result.put("orderId",id);result.put("totalAmount",order.getTotalAmount());result.put("canPrepare",canPrepare);result.put("canResume",canResume);result.put("paymentSessionId",canResume?preparedSession:null);result.put("status",order.getOrderStatus());result.put("deliveryAddress",OrderMapper.toDTO(order).getDeliveryAddress());return result;
    }
}
