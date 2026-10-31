package com.mercato.Commerce;

import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import java.time.Instant;
import java.util.*;

/** Pure unit selection. The caller holds the order lock and records its command in the same transaction. */
public final class UnitFinancials {
    private UnitFinancials(){}
    public static boolean attributed(Order order){return !order.getOrderLines().isEmpty() && order.getOrderLines().stream().allMatch(UnitFinancials::attributed);}
    public static boolean attributed(OrderLine line){
        if(line.getMoneyUnits().size()!=line.getOrderedQty() || line.getMoneyUnits().stream().anyMatch(u->u.getState()==null))return false;
        var counts=new HashMap<String,Integer>();for(var unit:line.getMoneyUnits())counts.merge(unit.getState().getStage(),1,Integer::sum);
        int shipped=counts.getOrDefault("SHIPPED",0)+counts.getOrDefault("RETURNED",0),packed=shipped+counts.getOrDefault("PACKED",0),preparing=packed+counts.getOrDefault("PREPARING",0),accepted=preparing+counts.getOrDefault("ACCEPTED",0);
        return shipped==line.getShippedQty() && packed==line.getPackedQty() && preparing==line.getPreparingQty() && accepted==line.getAcceptedQty() && counts.getOrDefault("CANCELLED",0)==line.getCancelledQty() && counts.getOrDefault("UNACCEPTED",0)==line.getOrderedQty()-line.getCancelledQty()-line.getAcceptedQty();
    }
    public static List<OrderMoneyUnit> units(Order order){return order.getOrderLines().stream().flatMap(l->l.getMoneyUnits().stream()).toList();}
    public static List<OrderMoneyUnit> stage(OrderLine line,String stage){return line.getMoneyUnits().stream().filter(u->u.getState()!=null && stage.equals(u.getState().getStage())).sorted(Comparator.comparingInt(OrderMoneyUnit::getUnitNumber)).toList();}
    public static List<OrderMoneyUnit> transition(OrderLine line,OrderLineAction action,int quantity,TransitionTrigger actor,String operation,Instant now){
        if(line.getMoneyUnits().isEmpty())return List.of(); // Legacy counters remain counters, never invented unit history.
        requireAttributed(line);
        List<String> buckets=switch(action){case ACCEPT->List.of("UNACCEPTED");case START_PREPARING->List.of("ACCEPTED");case MARK_PACKED->List.of("PREPARING");case SHIP->List.of("PACKED","PREPARING","ACCEPTED");case CANCEL->actor==TransitionTrigger.CUSTOMER?List.of("UNACCEPTED","ACCEPTED"):List.of("UNACCEPTED","ACCEPTED","PREPARING","PACKED");default->throw new CustomBadRequestException("Unsupported unit transition");};
        var selected=OrderMoneySnapshot.selectUnits(line,buckets.stream().map(s->stage(line,s).stream().map(OrderMoneyUnit::getUnitId).toList()).toList(),quantity);
        String target=switch(action){case ACCEPT->"ACCEPTED";case START_PREPARING->"PREPARING";case MARK_PACKED->"PACKED";case SHIP->"SHIPPED";case CANCEL->"CANCELLED";default->throw new IllegalStateException();};
        for(var unit:selected){var state=unit.getState();state.setStage(target);state.setOperationId(operation);if(action==OrderLineAction.SHIP)state.setShippedAt(now);if(action==OrderLineAction.CANCEL)state.setCause(actor==TransitionTrigger.CUSTOMER?"CUSTOMER_REQUEST":actor==TransitionTrigger.SYSTEM?"SYSTEM_SELLER_DELAY":actor==TransitionTrigger.SELLER?"SELLER_CANCELLATION":"ADMIN_ACTION");}
        return selected;
    }
    public static void requireAttributed(OrderLine line){if(!attributed(line))throw new CustomBadRequestException("UNIT_ATTRIBUTION_REVIEW_REQUIRED");}
    public static boolean closed(Order order){return attributed(order)&&units(order).stream().allMatch(u->Set.of("CANCELLED","RETURNED").contains(u.getState().getStage()));}
    public static long merchandiseCommitted(Order order,String unitId,boolean successfulOnly){return order.getPayment().getRefunds().stream().filter(r->successfulOnly?r.getStatus()==RefundStatus.SUCCESS:r.committed()).flatMap(r->r.getAllocations().stream()).filter(a->unitId.equals(a.getUnit().getUnitId())).mapToLong(RefundUnitAllocation::getMerchandisePaise).sum();}
}
