package com.mercato.Service;

import com.mercato.Entity.fulfillment.*;
import com.mercato.ExceptionHandler.*;
import com.mercato.Repository.*;
import com.mercato.Utils.AuthUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.*;

/** Prospective adoption never rewrites historical confirmation or invents missing evidence. */
@Service
public class WorkflowAdoption {
    public record Request(String orderId,String operationId,Long expectedVersion,String reason) {}
    private final OrderRepository orders;private final OrderReservationRepository reservations;
    private final FulfillmentWorkflow policy;private final FulfillmentFunding funding;private final WorkflowCommands commands;
    private final FulfillmentDeadlines deadlines;private final AuthUtil auth;private final JdbcTemplate jdbc;
    public WorkflowAdoption(OrderRepository orders,OrderReservationRepository reservations,FulfillmentWorkflow policy,FulfillmentFunding funding,WorkflowCommands commands,FulfillmentDeadlines deadlines,AuthUtil auth,JdbcTemplate jdbc){this.orders=orders;this.reservations=reservations;this.policy=policy;this.funding=funding;this.commands=commands;this.deadlines=deadlines;this.auth=auth;this.jdbc=jdbc;}
    @Transactional public FulfillmentWorkflow.Summary adopt(Request request,TransitionTrigger actor) {
        if(actor!=TransitionTrigger.ADMIN)throw new ForbiddenOperationException("Workflow adoption requires an administrator");
        if(request==null||request.orderId()==null||request.reason()==null||request.reason().isBlank()||request.reason().length()>500)throw new CustomBadRequestException("Order and adoption reason are required");
        var order=orders.lockByOrderId(request.orderId()).orElseThrow(()->new ResourceNotFoundException("Order","orderId",request.orderId()));
        String identity=auth.getLoggedInUser().getEmail();String body=commands.encode(Arrays.asList("ADOPT_WORKFLOW",request,actor,identity));
        String stored=commands.stored(order,request.operationId(),request.expectedVersion(),body);
        if(stored!=null)return commands.decode(stored,FulfillmentWorkflow.Summary.class);
        if(!order.isWorkflowReviewRequired()||order.getWorkflowPolicyAdoptedAt()!=null)throw new CustomBadRequestException("Order does not require initial workflow adoption");
        if(!funding.verified(order)||order.getAllocationCompletedAt()==null||order.getConfirmedAt()==null||order.getSellerActionsAvailableAt()==null||order.isInventoryFinalizationFailed()||order.isAutomaticFullRefund())throw new CustomBadRequestException("Verified funding, confirmation and allocation must be reconciled first");
        for(var line:order.getOrderLines()) {
            if(!line.validQuantities())throw new CustomBadRequestException("Invalid legacy quantities must be reconciled first");
            if(line.getPendingQty()>0){var r=reservations.findByOrderLine_Id(line.getId()).orElseThrow(()->new CustomBadRequestException("Legacy allocation must be reconciled first"));if(r.getReservedQty()!=line.getPendingQty()||!r.getOrder().getId().equals(order.getId()))throw new CustomBadRequestException("Legacy allocation quantity requires reconciliation");}
        }
        order.setWorkflowVersion(2);order.setWorkflowReviewRequired(false);order.setWorkflowReviewReason(null);order.setWorkflowPolicyAdoptedAt(policy.now());order.workflowChanged();
        order.getOrderLines().forEach(l->{l.setWorkflowLegacyReview(false);l.setWorkflowBaselineOrderedQty(l.getOrderedQty()-l.getCancelledQty());l.setWorkflowBaselineAcceptedQty(l.getAcceptedQty());l.setWorkflowBaselineAt(order.getWorkflowPolicyAdoptedAt());});orders.saveAndFlush(order);deadlines.schedule(order);
        var outcome=policy.summarize(order);commands.recordAggregate(order,request.operationId(),body,actor,identity,request.reason(),"ADOPT_WORKFLOW",0,commands.encode(outcome));
        jdbc.update("UPDATE fulfillment_work_items SET resolved_at=?,resolved_by=?,resolution_reason=? WHERE order_id=? AND kind='LEGACY_WORKFLOW_REVIEW'",Timestamp.from(policy.now()),identity,request.reason(),order.getOrderId());
        return outcome;
    }
}
