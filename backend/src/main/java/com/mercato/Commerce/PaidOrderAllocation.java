package com.mercato.Commerce;

import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import com.mercato.Jobs.*;
import com.mercato.Repository.*;
import com.mercato.Service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;

@Service
public class PaidOrderAllocation {
    private final OrderRepository orders;private final UserRepository users;private final CartService carts;
    private final OrderReservationService inventory;private final RefundObligations refunds;private final DurableJobs jobs;
    private final OrderReservationRepository reservations;
    private final FulfillmentDeadlines deadlines; private final java.time.Clock clock; private final FulfillmentFunding funding;
    public PaidOrderAllocation(OrderRepository orders,UserRepository users,CartService carts,OrderReservationService inventory,RefundObligations refunds,DurableJobs jobs,OrderReservationRepository reservations,FulfillmentDeadlines deadlines,java.time.Clock clock,FulfillmentFunding funding){this.funding=funding;this.deadlines=deadlines;this.clock=clock;this.orders=orders;this.users=users;this.carts=carts;this.inventory=inventory;this.refunds=refunds;this.jobs=jobs;this.reservations=reservations;}
    private Instant now(){return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
    @Transactional
    public void allocate(String orderId) {
        var order=orders.lockByOrderId(orderId).orElseThrow(()->new TransientJobException("Awaiting order"));
        if(order.getAllocationCompletedAt()!=null || order.isAutomaticFullRefund())return;
        if(!funding.verified(order))throw new PoisonJobException("Allocation requires verified capture");
        if(order.getOrderStatus()==OrderStatus.CANCELLED || order.getOrderLines().stream().anyMatch(l->l.getCancelledQty()>0)){
            // A gateway checkout amount is immutable. A cancellation before allocation
            // makes that checkout ineligible; never resurrect its cancelled quantities.
            refunds.full(order,"Capture received after cancellation");return;
        }
        if(order.isWorkflowReviewRequired())throw new ManualInterventionJobException("Legacy fulfillment requires review before SLA adoption");
        if(order.getOrderStatus()!=OrderStatus.CREATED){
            for(var line:order.getOrderLines())if(line.getPendingQty()>0){var reservation=reservations.findByOrderLine_Id(line.getId()).orElseThrow(()->new ManualInterventionJobException("Legacy allocation needs reconciliation"));if(reservation.getReservedQty()<line.getPendingQty())throw new ManualInterventionJobException("Legacy allocation quantity mismatch");}
            order.setAllocationCompletedAt(now());order.establishConfirmation(now());deadlines.schedule(order);return;
        }
        var buyer=users.findByEmail(order.getCustomerEmail());
        var cart=buyer.map(carts::getCartByUser).orElse(null);
        inventory.finalizeInventoryForPaidOrder(order,cart);
        order.confirmOrder();order.setAllocationCompletedAt(now());order.establishConfirmation(now());order.workflowChanged();order.setInventoryFinalizationFailed(false);deadlines.schedule(order);
        for(var line:order.getOrderLines())line.addStateTransition(StateTransition.builder().orderLine(line).fromStatus(OrderLineStatus.CREATED).toStatus(OrderLineStatus.CONFIRMED).action(OrderLineAction.CONFIRM).triggeredBy(TransitionTrigger.SYSTEM).reason("Verified payment allocated").occurredAt(now()).build());
        if(cart!=null&&!cart.isEmpty())carts.removePurchasedItems(cart,order);
        jobs.enqueue("AUTH_EMAIL","order-confirmed:"+orderId,Map.of("to",order.getCustomerEmail(),"subject","Your Mercato order is confirmed","text","Order "+orderId+" is confirmed. Total paid: ₹"+order.getPayment().getCapturedAmount()+". Follow progress in Your orders."));
    }
}
