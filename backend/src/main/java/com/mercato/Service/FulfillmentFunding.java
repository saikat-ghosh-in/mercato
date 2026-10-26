package com.mercato.Service;

import com.mercato.Entity.fulfillment.Order;
import org.springframework.stereotype.Service;

/** Extension point for verified authorization evidence in the payment task. */
@Service
public class FulfillmentFunding {
    public boolean verified(Order order) {
        return order != null && order.getTotalAmount() != null && order.getTotalAmount().signum() > 0
            && order.getPayment() != null && order.getPayment().getCapturedAt() != null
            && order.getPayment().getCapturedAmount() != null && order.getPayment().getCapturedAmount().signum() > 0
            && !order.getPayment().isFinancialHalt()
            && order.getPayment().getCapturedAmount().compareTo(order.getTotalAmount()) >= 0;
    }
}
