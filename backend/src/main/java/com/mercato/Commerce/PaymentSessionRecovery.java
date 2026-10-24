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
    public Object recover(String id){
        var order=orders.findByOrderIdAndCustomerEmail(id,auth.getLoggedInUser().getEmail()).orElseThrow(()->new ResourceNotFoundException("Order","orderId",id));
        var payment=order.getPayment();boolean canResume=order.getOrderStatus()==OrderStatus.CREATED&&payment!=null&&payment.getCapturedAt()==null&&payment.getPaymentSessionId()!=null;
        var result=new LinkedHashMap<String,Object>();result.put("orderId",id);result.put("totalAmount",order.getTotalAmount());result.put("canResume",canResume);result.put("paymentSessionId",canResume?payment.getPaymentSessionId():null);result.put("status",order.getOrderStatus());result.put("deliveryAddress",OrderMapper.toDTO(order).getDeliveryAddress());return result;
    }
}
