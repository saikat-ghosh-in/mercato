package com.mercato.order;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class FulfillmentWorkflowPolicyTest {
    static final Instant CONFIRMED=Instant.parse("2026-10-26T00:00:00Z");
    static OrderLine line() {
        var o=new Order();o.setOrderStatus(OrderStatus.CONFIRMED);o.establishConfirmation(CONFIRMED);o.setAllocationCompletedAt(CONFIRMED);
        o.setTotalAmount(BigDecimal.TEN);o.setCurrency("INR");
        var p=new Payment();p.setCapturedAt(CONFIRMED);p.setCapturedAmount(BigDecimal.TEN);o.setPayment(p);
        p.setAmount(BigDecimal.TEN);p.setCurrency("INR");
        var l=new OrderLine();l.setOrderedQty(10);l.setUnitPrice(BigDecimal.ONE);o.addOrderLine(l);return l;
    }
    FulfillmentWorkflow policy(Instant now){return new FulfillmentWorkflow(Clock.fixed(now,ZoneOffset.UTC),new FulfillmentFunding());}
    @Test void sellerUnlocksAtExactHourAndAcceptClosesAtExactDeadline(){
        var l=line();assertEquals(0,policy(CONFIRMED.plusSeconds(3599)).maximum(l,TransitionTrigger.SELLER,OrderLineAction.ACCEPT));
        assertEquals(10,policy(CONFIRMED.plusSeconds(3600)).maximum(l,TransitionTrigger.SELLER,OrderLineAction.ACCEPT));
        assertEquals(10,policy(CONFIRMED.plusSeconds(176399)).maximum(l,TransitionTrigger.SELLER,OrderLineAction.ACCEPT));
        assertEquals(0,policy(CONFIRMED.plusSeconds(176400)).maximum(l,TransitionTrigger.SELLER,OrderLineAction.ACCEPT));
    }
    @Test void cancellationConsumesLeastAdvancedBucketsAndNeverShippedHistory(){
        var l=line();l.accept(8);l.startPreparing(6);l.markPacked(4);l.ship(2);
        var p=policy(CONFIRMED.plusSeconds(7200));assertEquals(4,p.maximum(l,TransitionTrigger.CUSTOMER,OrderLineAction.CANCEL));
        assertEquals(8,p.maximum(l,TransitionTrigger.SELLER,OrderLineAction.CANCEL));
        l.cancel(5);assertEquals(5,l.getAcceptedQty());assertEquals(5,l.getPreparingQty());assertEquals(4,l.getPackedQty());assertEquals(2,l.getShippedQty());assertTrue(l.validQuantities());
        l.cancel(3);assertEquals(2,l.getAcceptedQty());assertEquals(2,l.getPackedQty());assertEquals(2,l.getShippedQty());assertTrue(l.validQuantities());
    }
    @Test void sellerCannotPerformPhysicalShipmentAndCustomerCanCancelAfterAnHour(){
        var l=line();l.accept(10);l.startPreparing(3);l.markPacked(3);var p=policy(CONFIRMED.plusSeconds(7200));
        assertEquals(0,p.maximum(l,TransitionTrigger.SELLER,OrderLineAction.SHIP));assertEquals(7,p.maximum(l,TransitionTrigger.CUSTOMER,OrderLineAction.CANCEL));
    }
    @Test void invalidLegacyRowsAndUnverifiedFundsStayLocked(){
        var l=line();l.getOrder().getPayment().setCapturedAt(null);assertEquals(0,policy(CONFIRMED.plusSeconds(7200)).maximum(l,TransitionTrigger.SELLER,OrderLineAction.ACCEPT));
        l=line();l.setAcceptedQty(11);assertEquals(0,policy(CONFIRMED.plusSeconds(7200)).maximum(l,TransitionTrigger.SELLER,OrderLineAction.CANCEL));
    }
    @Test void directShippingPreservesMixedUnitAttributionAndRequiresAcceptance(){
        var l=line();
        for(int n=1;n<=10;n++){var u=new OrderMoneyUnit();u.setUnitId("unit-"+n);u.setUnitNumber(n);u.setOrderLine(l);u.setState(OrderUnitState.initial(u));l.getMoneyUnits().add(u);}
        com.mercato.Commerce.UnitFinancials.transition(l,OrderLineAction.ACCEPT,8,TransitionTrigger.SELLER,"accept",CONFIRMED);l.accept(8);
        com.mercato.Commerce.UnitFinancials.transition(l,OrderLineAction.START_PREPARING,5,TransitionTrigger.SELLER,"prepare",CONFIRMED);l.startPreparing(5);
        com.mercato.Commerce.UnitFinancials.transition(l,OrderLineAction.MARK_PACKED,2,TransitionTrigger.SELLER,"pack",CONFIRMED);l.markPacked(2);
        com.mercato.Commerce.UnitFinancials.transition(l,OrderLineAction.SHIP,6,TransitionTrigger.ADMIN,"ship",CONFIRMED);l.ship(6);
        assertEquals(6,l.getPreparingQty());assertEquals(6,l.getPackedQty());assertEquals(6,l.getShippedQty());
        assertTrue(l.validQuantities());assertTrue(com.mercato.Commerce.UnitFinancials.attributed(l));
        assertThrows(IllegalArgumentException.class,()->l.ship(3));
    }
}
