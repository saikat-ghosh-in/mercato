package com.mercato.Service;

import com.mercato.Entity.fulfillment.Order;
import org.springframework.stereotype.Service;

/** Extension point for verified authorization evidence in the payment task. */
@Service
public class FulfillmentFunding {
    public boolean verified(Order order) {
        return order.getPayment() != null && order.getPayment().getCapturedAt() != null
            && order.getPayment().getCapturedAmount() != null && order.getPayment().getCapturedAmount().signum() > 0;
    }
}
