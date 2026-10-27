package com.mercato.Service;

import com.mercato.Entity.fulfillment.Order;
import org.springframework.stereotype.Service;

/** Extension point for verified authorization evidence in the payment task. */
@Service
public class FulfillmentFunding {
    /** Current reader proof of actual collection, independent from fulfillment halt/entitlement. */
    public static boolean provenCollection(Order order){
        return order!=null && order.getPayment()!=null && order.getPayment().getCaptureVerifiedAt()!=null
            && order.getPayment().getCaptureEvidenceId()!=null && !order.getPayment().getCaptureEvidenceId().isBlank()
            && order.getPayment().getCapturedAmount()!=null && order.getPayment().getCapturedAmount().signum()>0;
    }
    public boolean verified(Order order) {
        if(!verifiedCollection(order))return false;
        if(order.getOrderLines().stream().anyMatch(l->!l.getMoneyUnits().isEmpty())&&!com.mercato.Commerce.UnitFinancials.attributed(order))return false;
        long committed=order.getPayment().getRefunds().stream().filter(com.mercato.Entity.fulfillment.payment.Refund::committed).mapToLong(r->com.mercato.Commerce.OrderMoneySnapshot.paise(r.getAmount())).sum();
        if(committed==0)return true;
        if(!com.mercato.Commerce.UnitFinancials.attributed(order))return false;
        for(var refund:order.getPayment().getRefunds())if(refund.committed() && refund.getAllocations().stream().mapToLong(com.mercato.Entity.fulfillment.payment.RefundUnitAllocation::totalPaise).sum()!=com.mercato.Commerce.OrderMoneySnapshot.paise(refund.getAmount()))return false;
        long required=0;
        for(var unit:com.mercato.Commerce.UnitFinancials.units(order)){
            String stage=unit.getState().getStage();
            if(!java.util.Set.of("CANCELLED","RETURNED").contains(stage))required=Math.addExact(required,unit.buyerTotalPaise());
            else if(stage.equals("CANCELLED") && "CUSTOMER_REQUEST".equals(unit.getState().getCause())){
                long fixed=unit.getPlatformPaise()+unit.getHandlingPaise();
                for(var refund:order.getPayment().getRefunds())if(refund.committed())for(var a:refund.getAllocations())if(a.getUnit().getUnitId().equals(unit.getUnitId()))fixed-=a.getPlatformPaise()+a.getHandlingPaise();
                required=Math.addExact(required,Math.max(0,fixed));
            }
        }
        return com.mercato.Commerce.OrderMoneySnapshot.paise(order.getPayment().getCapturedAmount())-committed>=required;
    }
    public static boolean verifiedCollection(Order order) {
        return order != null && order.getTotalAmount() != null && order.getTotalAmount().signum() > 0
            && order.getPayment() != null && order.getPayment().getCapturedAt() != null
            && order.getPayment().getCapturedAmount() != null && order.getPayment().getCapturedAmount().signum() > 0
            && !order.getPayment().isFinancialHalt()
            && order.getPayment().getCapturedAmount().compareTo(order.getTotalAmount()) >= 0;
    }
}
