package com.mercato.Service;

import com.mercato.Entity.fulfillment.*;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class FulfillmentWorkflow {
    private final Clock clock;
    private final FulfillmentFunding funding;
    public FulfillmentWorkflow(Clock clock, FulfillmentFunding funding) { this.clock=clock; this.funding=funding; }
    public Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    public record Projection(String state, long version, int preparingQty, int packedQty, int deliveredQty,
            Instant confirmedAt, Instant sellerActionsAvailableAt, Instant acceptDeadline, Instant preparationReviewAt,
            Instant workflowPolicyAdoptedAt, Instant effectiveSellerActionsAvailableAt, Instant serverNow, Map<String,Integer> actionMaxima, Map<String,String> blockedReasons, List<String> allowedActions, boolean reviewRequired, String reviewReason) {}
    public Projection project(OrderLine line, TransitionTrigger actor) {
        var o=line.getOrder(); var now=now(); var maxima=new LinkedHashMap<String,Integer>();var reasons=new LinkedHashMap<String,String>();
        for(var action:List.of(OrderLineAction.ACCEPT,OrderLineAction.START_PREPARING,OrderLineAction.MARK_PACKED,OrderLineAction.CANCEL,OrderLineAction.SHIP)) {
            String blocked=blocked(line,actor,action,now); int max=blocked==null?quantityMax(line,actor,action):0;
            if(blocked==null&&max==0)blocked="NO_ELIGIBLE_QUANTITY";
            maxima.put(action.name(),max); if(blocked!=null)reasons.put(action.name(),blocked);
        }
        String state=state(line.getOrderedQty(),line.getCancelledQty(),line.getAcceptedQty(),line.getPreparingQty(),line.getPackedQty(),line.getShippedQty(),line.getDeliveredQty(),o.getConfirmedAt()!=null);
        return new Projection(state,o.getWorkflowRevision(),line.getPreparingQty(),line.getPackedQty(),line.getDeliveredQty(),o.getConfirmedAt(),o.getSellerActionsAvailableAt(),deadline(o),preparationReviewAt(o),o.getWorkflowPolicyAdoptedAt(),releaseAt(o),now,maxima,reasons,maxima.entrySet().stream().filter(e->e.getValue()>0).map(Map.Entry::getKey).toList(),o.isWorkflowReviewRequired(),o.getWorkflowReviewReason());
    }
    public record Summary(String state,long version,int workflowVersion,boolean reviewRequired,String reviewReason,boolean fundingVerified,
            int orderedQty,int cancelledQty,int acceptedQty,int preparingQty,int packedQty,int shippedQty,int deliveredQty,
            Instant confirmedAt,Instant sellerActionsAvailableAt,Instant acceptDeadline,Instant preparationReviewAt,Instant workflowPolicyAdoptedAt,Instant effectiveSellerActionsAvailableAt,Instant serverNow) {}
    public Summary summarize(Order o) {return summarize(o,o.getOrderLines());}
    public Summary summarize(Order o,Collection<OrderLine> lines) {
        int ordered=0,cancelled=0,accepted=0,preparing=0,packed=0,shipped=0,delivered=0;
        for(var l:lines){ordered+=l.getOrderedQty();cancelled+=l.getCancelledQty();accepted+=l.getAcceptedQty();preparing+=l.getPreparingQty();packed+=l.getPackedQty();shipped+=l.getShippedQty();delivered+=l.getDeliveredQty();}
        return new Summary(state(ordered,cancelled,accepted,preparing,packed,shipped,delivered,o.getConfirmedAt()!=null),o.getWorkflowRevision(),o.getWorkflowVersion(),o.isWorkflowReviewRequired(),o.getWorkflowReviewReason(),funding.verified(o),ordered,cancelled,accepted,preparing,packed,shipped,delivered,o.getConfirmedAt(),o.getSellerActionsAvailableAt(),deadline(o),preparationReviewAt(o),o.getWorkflowPolicyAdoptedAt(),releaseAt(o),now());
    }
    private String state(int ordered,int cancelled,int accepted,int preparing,int packed,int shipped,int delivered,boolean confirmed) {
        int active=ordered-cancelled;
        if(ordered>0&&active==0)return "CANCELLED";
        if(!confirmed)return "AWAITING_FUNDING";
        if(delivered==active&&active>0)return "DELIVERED";
        if(shipped==active&&active>0)return "SHIPPED";
        if(packed==active&&active>0)return "PACKED";
        if(preparing==active&&active>0)return "PREPARING";
        if(accepted==active&&active>0)return "ACCEPTED";
        return "CONFIRMED";
    }
    public Instant releaseAt(Order o) {
        if(o.getSellerActionsAvailableAt()==null)return null;
        return o.getWorkflowPolicyAdoptedAt()!=null&&o.getWorkflowPolicyAdoptedAt().isAfter(o.getSellerActionsAvailableAt())?o.getWorkflowPolicyAdoptedAt():o.getSellerActionsAvailableAt();
    }
    public Instant preparationReviewAt(Order o) {var anchor=o.getWorkflowPolicyAdoptedAt()!=null?o.getWorkflowPolicyAdoptedAt():o.getConfirmedAt();return anchor==null?null:anchor.plusSeconds(172800);}
    public Instant deadline(Order o) { return releaseAt(o)==null?null:releaseAt(o).plusSeconds(172800); }
    public int maximum(OrderLine line,TransitionTrigger actor,OrderLineAction action) {
        return blocked(line,actor,action,now())==null?quantityMax(line,actor,action):0;
    }
    public void require(OrderLine line,TransitionTrigger actor,OrderLineAction action,int qty) {
        String blocked=blocked(line,actor,action,now());
        if(blocked!=null)throw new CustomBadRequestException(blocked);
        if(qty<=0||qty>quantityMax(line,actor,action))throw new CustomBadRequestException("Quantity exceeds eligible units");
    }
    private String blocked(OrderLine line,TransitionTrigger actor,OrderLineAction action,Instant now) {
        var o=line.getOrder();
        if(o.isWorkflowReviewRequired() || o.getWorkflowVersion()!=2 || !line.validQuantities())return "WORKFLOW_REVIEW_REQUIRED";
        if(actor==TransitionTrigger.CUSTOMER && action!=OrderLineAction.CANCEL)return "CUSTOMER_CANCEL_ONLY";
        if(actor==TransitionTrigger.SELLER && action==OrderLineAction.SHIP)return "LOGISTICS_HANDOFF_REQUIRED";
        if(action==OrderLineAction.CANCEL && (actor==TransitionTrigger.CUSTOMER||actor==TransitionTrigger.ADMIN) && o.getConfirmedAt()==null && o.getAllocationCompletedAt()==null && !funding.verified(o))return null;
        if(!funding.verified(o)||o.getAllocationCompletedAt()==null||o.getConfirmedAt()==null||o.isInventoryFinalizationFailed())return "VERIFIED_FUNDING_AND_ALLOCATION_REQUIRED";
        if(actor==TransitionTrigger.SELLER && (releaseAt(o)==null||now.isBefore(releaseAt(o))))return "SELLER_HOLD";
        if(action==OrderLineAction.ACCEPT && deadline(o)!=null && !now.isBefore(deadline(o)))return "ACCEPT_DEADLINE_EXPIRED";
        return null;
    }
    private int quantityMax(OrderLine l,TransitionTrigger actor,OrderLineAction action) {
        return switch(action) {
            case ACCEPT -> l.getOrderedQty()-l.getCancelledQty()-l.getAcceptedQty();
            case START_PREPARING -> l.getAcceptedQty()-l.getPreparingQty();
            case MARK_PACKED -> l.getPreparingQty()-l.getPackedQty();
            case SHIP -> l.getPackedQty()-l.getShippedQty();
            case CANCEL -> l.getOrderedQty()-l.getCancelledQty()-(actor==TransitionTrigger.CUSTOMER?l.getPreparingQty():l.getShippedQty());
            default -> 0;
        };
    }
}
