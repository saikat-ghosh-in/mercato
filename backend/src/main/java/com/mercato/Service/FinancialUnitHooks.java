package com.mercato.Service;

import com.mercato.Commerce.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.ExceptionHandler.*;
import com.mercato.Repository.OrderRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

/** Small finance seams for logistics. Physical receipt/delivery/restock belongs to the caller. */
@Service @RequiredArgsConstructor
public class FinancialUnitHooks {
    private final OrderRepository orders;private final FulfillmentFunding funding;private final RefundObligations refunds;
    private final WorkflowCommands commands;private final AuthUtil auth;private final Clock clock;private final SellerOrderVisibility visibility;
    public record UnitView(String unitId,int orderLineNumber,String fulfillmentId,String stage,String refundState,long buyerTotalPaise){}
    @Transactional(readOnly=true)
    public List<UnitView> units(String orderId){
        var actor=auth.getLoggedInUser();var o=orders.findByOrderId(orderId).orElseThrow(()->new ResourceNotFoundException("Order","orderId",orderId));
        boolean buyer=actor.getUserId()!=null&&actor.getUserId().equals(o.getCustomerUserId()) || actor.getEmail().equals(o.getCustomerEmail());
        if(!actor.isAdmin()&&!buyer&&o.getOrderLines().stream().noneMatch(l->actor.isSeller()&&actor.getEmail().equals(l.getSellerEmail())))throw new ForbiddenOperationException("Order ownership required");
        if(!actor.isAdmin()&&!buyer)visibility.requireVisible(o);
        var out=new ArrayList<UnitView>();
        for(var l:o.getOrderLines())if(actor.isAdmin()||buyer||actor.getEmail().equals(l.getSellerEmail()))for(var u:l.getMoneyUnits()){
            String state="NONE";long committed=0,success=0;boolean attention=false,waiting=false;
            for(var r:o.getPayment().getRefunds())for(var a:r.getAllocations())if(a.getUnit().getUnitId().equals(u.getUnitId())){if(r.committed())committed+=a.totalPaise();if(r.getStatus()==com.mercato.Entity.fulfillment.payment.RefundStatus.SUCCESS)success+=a.totalPaise();if(r.getStatus()==com.mercato.Entity.fulfillment.payment.RefundStatus.FAILED||"NEEDS_ATTENTION".equals(r.getFailureReason()))attention=true;if("WAITING_FOR_FUNDS".equals(r.getFailureReason()))waiting=true;}
            if(attention)state="NEEDS_ATTENTION";else if(waiting)state="WAITING_FOR_FUNDS";else if(committed>success)state="PENDING";else if(success>0)state=success>=u.buyerTotalPaise()?"REFUNDED":"PARTIALLY_REFUNDED";
            out.add(new UnitView(u.getUnitId(),l.getOrderLineNumber(),l.getFulfillmentId(),u.getState()==null?"REVIEW_REQUIRED":u.getState().getStage(),state,u.buyerTotalPaise()));
        }
        return out;
    }
    /** Caller holds the order lock; validates selected packed financial identities without inventing shipment. */
    public List<OrderMoneyUnit> requireShippable(Order order,Collection<String> ids){
        if(!funding.verified(order)||!FulfillmentFunding.provenCollection(order)||order.getConfirmedAt()==null||order.getAllocationCompletedAt()==null||order.isWorkflowReviewRequired())throw new CustomBadRequestException("VERIFIED_UNIT_FUNDING_AND_ALLOCATION_REQUIRED");
        return select(order,ids,"PACKED");
    }
    /**
     * Task5 holds the order lock and owns the parcel command's operation/version guard.
     * Call only after establishing its actual shipment, in the same transaction as line.ship,
     * inventory settlement and that one command record. This hook records no command or version.
     */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<OrderMoneyUnit> recordShippedUnits(Order order,Collection<String> ids,String operationId){
        if(!auth.getLoggedInUser().isAdmin())throw new ForbiddenOperationException("Admin shipment required");
        WorkflowCommands.requireExternalOperation(operationId);
        var selected=requireShippable(order,ids);
        for(var unit:selected){unit.getState().setStage("SHIPPED");unit.getState().setOperationId(operationId);unit.getState().setShippedAt(clock.instant());}
        return selected;
    }
    public List<OrderMoneyUnit> refundableShippedUnits(Order order){
        if(!UnitFinancials.attributed(order))throw new CustomBadRequestException("UNIT_ATTRIBUTION_REVIEW_REQUIRED");
        return UnitFinancials.units(order).stream().filter(u->u.getState().getStage().equals("SHIPPED")&&u.getState().getShippedAt()!=null).toList();
    }
    /** Task5 must validate and persist actual received/approved return units before calling, in this same transaction. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void approveReceivedUnits(String orderId,Collection<String> ids,String receiptId,String operationId,Long expectedVersion){
        var actor=auth.getLoggedInUser();if(!actor.isAdmin())throw new ForbiddenOperationException("Admin return approval required");
        if(receiptId==null||receiptId.isBlank()||receiptId.length()>120)throw new CustomBadRequestException("Validated received-return reference required");
        WorkflowCommands.requireExternalOperation(operationId);
        var order=orders.lockByOrderId(orderId).orElseThrow(()->new ResourceNotFoundException("Order","orderId",orderId));
        var sorted=ids==null?List.<String>of():ids.stream().sorted().toList();
        String body=commands.encode(Arrays.asList("APPROVE_RECEIVED_RETURN",sorted,receiptId,expectedVersion,actor.getEmail()));
        if(commands.stored(order,operationId,expectedVersion,body)!=null)return;
        if(!FulfillmentFunding.verifiedCollection(order)||!FulfillmentFunding.provenCollection(order))throw new CustomBadRequestException("VERIFIED_CAPTURE_REQUIRED");
        var selected=select(order,sorted,"SHIPPED");
        for(var unit:selected){if(unit.getState().getShippedAt()==null)throw new CustomBadRequestException("Explicit shipment attribution required");unit.getState().setStage("RETURNED");unit.getState().setCause("RETURN_APPROVED");unit.getState().setOperationId(operationId);unit.getState().setReturnApprovedAt(clock.instant());}
        order.workflowChanged();commands.recordAggregate(order,operationId,body,TransitionTrigger.ADMIN,actor.getEmail(),receiptId,"RETURN_APPROVED",selected.size(),"{}");refunds.approvedReturn(order);
    }
    private List<OrderMoneyUnit> select(Order order,Collection<String> ids,String stage){
        if(ids==null||ids.isEmpty()||new HashSet<>(ids).size()!=ids.size())throw new CustomBadRequestException("Unique selected unit identities required");
        if(!UnitFinancials.attributed(order))throw new CustomBadRequestException("UNIT_ATTRIBUTION_REVIEW_REQUIRED");
        var selected=UnitFinancials.units(order).stream().filter(u->ids.contains(u.getUnitId())).toList();
        if(selected.size()!=ids.size()||selected.stream().anyMatch(u->!u.getState().getStage().equals(stage)))throw new CustomBadRequestException("Selected units are outside the eligible scope");
        return selected;
    }
}
