package com.mercato.Commerce;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderStatus;

/** Current eligibility for the immutable original-total checkout; caller owns any required lock. */
public final class CheckoutPayability {
    private CheckoutPayability() {}
    public static boolean payable(Order order) {
        var payment=order.getPayment();
        return order.getOrderStatus()==OrderStatus.CREATED && order.getConfirmedAt()==null
            && order.getAllocationCompletedAt()==null && !order.isAutomaticFullRefund()
            && payment!=null && payment.getCapturedAt()==null
            && (payment.getCapturedAmount()==null || payment.getCapturedAmount().signum()==0)
            && payment.getAuthorizedAt()==null && payment.getAuthorizationId()==null
            && (payment.getAuthorizedAmount()==null || payment.getAuthorizedAmount().signum()==0)
            && !payment.isFinancialHalt() && !order.getOrderLines().isEmpty()
            && order.getOrderLines().stream().allMatch(line->line.getOrderedQty()>0
                && line.getCancelledQty()==0 && line.getPendingQty()==line.getOrderedQty());
    }
}
