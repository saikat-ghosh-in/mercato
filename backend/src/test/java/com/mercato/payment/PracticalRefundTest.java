package com.mercato.payment;
import com.mercato.Commerce.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Repository.*;
import com.mercato.Jobs.*;
import com.mercato.Service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PracticalRefundTest {
    private static SellerOrderVisibility visibility(){var c=java.time.Clock.systemUTC();return new SellerOrderVisibility(new FulfillmentWorkflow(c,new FulfillmentFunding()),c);}

    static Order order(){
        var o=new Order();o.setOrderId("ATTRIBUTED");o.setOrderStatus(OrderStatus.CREATED);o.setCustomerEmail("buyer@test.invalid");o.setTotalAmount(new BigDecimal("242"));o.setFinancialPolicyVersion(1);
        var l=new OrderLine();l.setOrderLineNumber(1);l.setFulfillmentId("F");l.setSellerEmail("seller@test.invalid");l.setUnitPrice(new BigDecimal("100"));l.setLineTotal(new BigDecimal("200"));l.setOrderedQty(2);l.setOrderLineStatus(OrderLineStatus.CONFIRMED);o.addOrderLine(l);
        for(int n=1;n<=2;n++){var u=new OrderMoneyUnit();u.setUnitId("U"+n);u.setUnitNumber(n);u.setOrderLine(l);u.setFinancialPolicyVersion(1);u.setNetMerchandisePaise(10000);u.setBuyerNormalDeliveryPaise(1000);u.setBuyerExpeditedDeliveryPaise(100);u.setPlatformPaise(900);u.setHandlingPaise(100);u.setCommissionPaise(50L);u.setState(OrderUnitState.initial(u));l.getMoneyUnits().add(u);}
        var p=new Payment();p.setId(1L);p.setCurrency("INR");p.setCapturedAt(Instant.now());p.setCapturedAmount(o.getTotalAmount());p.setGatewayReference("primary");p.setCaptureEvidenceId("test-reader-proof");p.setCaptureVerifiedAt(p.getCapturedAt());o.attachPayment(p);return o;
    }
    @Test void customerPartialCancellationRefundsUnusedQuotedDelivery(){
        var o=order();UnitFinancials.transition(o.getOrderLines().get(0),OrderLineAction.CANCEL,1,TransitionTrigger.CUSTOMER,"cancel",Instant.now());o.getOrderLines().get(0).cancel(1);
        var repo=mock(RefundRepository.class);when(repo.findByPayment_IdOrderByIdAsc(1L)).thenReturn(List.of());when(repo.findByRefundId(anyString())).thenReturn(Optional.empty());
        new RefundObligations(repo,mock(OrderRepository.class),mock(DurableJobs.class),visibility()).cancelled(o);
        var saved=org.mockito.ArgumentCaptor.forClass(Refund.class);verify(repo).saveAndFlush(saved.capture());assertEquals(0,new BigDecimal("111").compareTo(saved.getValue().getAmount()));
    }
    @Test void mixedCausesAndFinalClosureRefundAllActualFundsExactlyOnce(){
        var o=order();var l=o.getOrderLines().get(0);var history=new ArrayList<Refund>();var repo=mock(RefundRepository.class);
        when(repo.findByPayment_IdOrderByIdAsc(1L)).thenReturn(history);when(repo.findByRefundId(anyString())).thenAnswer(c->history.stream().filter(r->r.getRefundId().equals(c.getArgument(0))).findFirst());
        when(repo.saveAndFlush(any())).thenAnswer(c->{Refund r=c.getArgument(0);history.add(r);return r;});var service=new RefundObligations(repo,mock(OrderRepository.class),mock(DurableJobs.class),visibility());
        UnitFinancials.transition(l,OrderLineAction.CANCEL,1,TransitionTrigger.CUSTOMER,"one",Instant.now());l.cancel(1);service.cancelled(o);
        assertEquals(new BigDecimal("111.00"),history.get(0).getAmount());assertTrue(new com.mercato.Service.FulfillmentFunding().verified(o));
        service.cancelled(o);assertEquals(1,history.size());
        UnitFinancials.transition(l,OrderLineAction.CANCEL,1,TransitionTrigger.SELLER,"two",Instant.now());l.cancel(1);service.cancelled(o);
        assertEquals(new BigDecimal("131.00"),history.get(1).getAmount());assertEquals(new BigDecimal("242.00"),history.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(3,history.stream().mapToInt(r->r.getAllocations().size()).sum());service.cancelled(o);assertEquals(2,history.size());
    }
    @Test void selectionUsesLeastAdvancedIdentitiesWithoutChangingShippedHistory(){
        var o=order();var l=o.getOrderLines().get(0);
        UnitFinancials.transition(l,OrderLineAction.ACCEPT,1,TransitionTrigger.SELLER,"a",Instant.now());l.accept(1);
        UnitFinancials.transition(l,OrderLineAction.START_PREPARING,1,TransitionTrigger.SELLER,"p",Instant.now());l.startPreparing(1);
        UnitFinancials.transition(l,OrderLineAction.MARK_PACKED,1,TransitionTrigger.SELLER,"k",Instant.now());l.markPacked(1);
        assertEquals("U2",UnitFinancials.transition(l,OrderLineAction.CANCEL,1,TransitionTrigger.CUSTOMER,"c",Instant.now()).get(0).getUnitId());l.cancel(1);
        UnitFinancials.transition(l,OrderLineAction.SHIP,1,TransitionTrigger.ADMIN,"s",Instant.now());l.ship(1);
        assertEquals("SHIPPED",l.getMoneyUnits().get(0).getState().getStage());assertThrows(IllegalArgumentException.class,()->UnitFinancials.transition(l,OrderLineAction.CANCEL,1,TransitionTrigger.SELLER,"x",Instant.now()));
    }
    @Test void cancellationAfterApprovedReturnRefundsResidualFixedFeesAndReplays(){
        var o=order();var l=o.getOrderLines().get(0);var history=new ArrayList<Refund>();var repo=mock(RefundRepository.class);
        when(repo.findByPayment_IdOrderByIdAsc(1L)).thenReturn(history);when(repo.findByRefundId(anyString())).thenAnswer(c->history.stream().filter(r->r.getRefundId().equals(c.getArgument(0))).findFirst());
        when(repo.saveAndFlush(any())).thenAnswer(c->{Refund r=c.getArgument(0);history.add(r);return r;});var service=new RefundObligations(repo,mock(OrderRepository.class),mock(DurableJobs.class),visibility());
        var returned=l.getMoneyUnits().get(0).getState();returned.setStage("RETURNED");returned.setCause("RETURN_APPROVED");returned.setShippedAt(Instant.now());returned.setReturnApprovedAt(Instant.now());l.setAcceptedQty(1);l.setPreparingQty(1);l.setPackedQty(1);l.setShippedQty(1);
        service.approvedReturn(o);assertEquals(new BigDecimal("121.00"),history.get(0).getAmount());
        UnitFinancials.transition(l,OrderLineAction.CANCEL,1,TransitionTrigger.CUSTOMER,"last-cancel",Instant.now());l.cancel(1);service.cancelled(o);
        assertEquals(new BigDecimal("242.00"),history.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        service.cancelled(o);assertEquals(2,history.size());assertEquals(1,l.getShippedQty());
    }
    @Test void unassignedRefundCannotConsumeSurvivingUnitFunding(){var o=order();var r=Refund.builder().amount(new BigDecimal("1")).status(RefundStatus.PENDING).build();o.getPayment().getRefunds().add(r);assertFalse(new com.mercato.Service.FulfillmentFunding().verified(o));}
    @Test void unexpectedPartialCaptureAndHaltNeverGrantEntitlement(){var o=order();o.getPayment().setCapturedAmount(new BigDecimal("121"));assertFalse(new com.mercato.Service.FulfillmentFunding().verified(o));o.getPayment().setCapturedAmount(o.getTotalAmount());o.getPayment().setFinancialHalt(true);assertFalse(new com.mercato.Service.FulfillmentFunding().verified(o));}
    @Test void historicalObservationWithoutReaderProofIsNotCollection(){var o=order();o.getPayment().setCaptureEvidenceId(null);o.getPayment().setCaptureVerifiedAt(null);assertFalse(MarketplaceFinanceService.paid(o));}
    @Test void provenHaltedPartialCollectionIsVisibleButCannotEarnOrDispatch(){
        var o=order();var p=o.getPayment();p.setCaptureEvidenceId("reader-proof");p.setCaptureVerifiedAt(Instant.now());p.setCapturedAmount(new BigDecimal("100"));p.setFinancialHalt(true);p.setFinancialHaltReason("PARTIAL_CAPTURE_REQUIRES_UNIT_ATTRIBUTION");assertTrue(MarketplaceFinanceService.paid(o));assertFalse(new com.mercato.Service.FulfillmentFunding().verified(o));
        var repo=mock(OrderRepository.class);when(repo.financialOrders(any(),any(),any(),any())).thenReturn(List.of(o));var promos=mock(SellerPromotionRepository.class);when(promos.findAll()).thenReturn(List.of());var auth=mock(com.mercato.Utils.AuthUtil.class);var admin=new com.mercato.Entity.EcommUser();admin.getRoles().add(new com.mercato.Entity.Role(com.mercato.Entity.AppRole.ROLE_ADMIN));when(auth.getLoggedInUser()).thenReturn(admin);
        var result=(Map<?,?>)new MarketplaceFinanceService(repo,promos,auth,mock(UserRepository.class),visibility()).report(null,null,true);assertEquals(new BigDecimal("100.00"),result.get("capturedPayments"));assertEquals(new BigDecimal("0.00"),((Map<?,?>)result.get("totals")).get("fulfilledSales"));assertEquals(new BigDecimal("0.00"),((Map<?,?>)result.get("totals")).get("knownNetEarnings"));
    }
    @Test void genericSuccessIsNeverProofOfCollection(){var o=order();o.getPayment().setCapturedAt(null);o.getPayment().setCapturedAmount(null);o.setPaymentStatus(PaymentStatus.SUCCESS);assertFalse(MarketplaceFinanceService.paid(o));}
}

