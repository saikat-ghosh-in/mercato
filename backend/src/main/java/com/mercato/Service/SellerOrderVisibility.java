package com.mercato.Service;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Seller reads and commands share the workflow's effective release and server clock. */
@Service
public class SellerOrderVisibility {
    // Equivalent database predicate, applied before seller sorting/aggregation/limits.
    public static final String ORDER_PREDICATE = "o.confirmedAt is not null and o.sellerActionsAvailableAt is not null and o.sellerActionsAvailableAt <= :visibleAt and (o.workflowPolicyAdoptedAt is null or o.workflowPolicyAdoptedAt <= :visibleAt)";
    private final FulfillmentWorkflow workflow;
    private final Clock clock;
    public SellerOrderVisibility(FulfillmentWorkflow workflow, Clock clock) { this.workflow=workflow;this.clock=clock; }
    public Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    public boolean visible(Order order) { return visible(order,now()); }
    public boolean visible(Order order,Instant at) {
        Instant release=workflow.releaseAt(order);
        return order.getConfirmedAt()!=null && release!=null && !at.isBefore(release);
    }
    public void requireVisible(Order order) {
        if(!visible(order))throw new ResourceNotFoundException("Order","orderId",order.getOrderId());
    }
}
