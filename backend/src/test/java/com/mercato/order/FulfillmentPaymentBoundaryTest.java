package com.mercato.order;

import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.Payloads.Request.OrderLineUpdateRequestDTO;
import com.mercato.Repository.*;
import com.mercato.Service.*;
import com.mercato.Utils.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FulfillmentPaymentBoundaryTest {
    private final OrderLineRepository lines = mock(OrderLineRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderReservationService reservations = mock(OrderReservationService.class);
    private final RefundService refunds = mock(RefundService.class);
    private final ShipmentNotificationService mail = mock(ShipmentNotificationService.class);
    private final AuthUtil auth = mock(AuthUtil.class);
    private final OrderLineUpdateServiceImpl service = new OrderLineUpdateServiceImpl(lines, orders, reservations, refunds, mail, auth);
    private Order order;
    private OrderLine line;

    @BeforeEach void prepare() {
        order = new Order(); order.setOrderId("ORD-BOUNDARY"); order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        Payment payment = new Payment(); payment.setStatus(PaymentStatus.SUCCESS); payment.setCompletedAt(Instant.now());
        order.setPayment(payment);
        line = new OrderLine(); line.setId(1L); line.setOrderedQty(2); line.setUnitPrice(new BigDecimal("100.00"));
        line.setOrderLineStatus(OrderLineStatus.CONFIRMED); order.addOrderLine(line);
        when(lines.findByFulfillmentIdAndOrderLineNumberForUpdate("FUL-1", 1)).thenReturn(Optional.of(line));
        when(orders.lockByFulfillment("FUL-1",1)).thenReturn(Optional.of(order));
    }
    private OrderLineUpdateRequestDTO request(OrderLineAction action) {
        var request = new OrderLineUpdateRequestDTO(); request.setFulfillmentId("FUL-1"); request.setOrderLineNumber(1);
        request.setAction(action); request.setQty(2); return request;
    }
    @Test void cancellationBeforeAcceptanceClosesThePaidOrder() {
        service.updateOrderLine(request(OrderLineAction.CANCEL), TransitionTrigger.ADMIN);
        assertEquals(OrderLineStatus.CANCELLED, line.getOrderLineStatus());
        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertEquals(2, line.getCancelledQty());
    }
    @Test void unpaidOrderCannotShipOrChangeQuantities() {
        order.setOrderStatus(OrderStatus.CREATED); order.setPaymentStatus(PaymentStatus.INITIATED);
        line.setOrderLineStatus(OrderLineStatus.CREATED);
        assertThrows(CustomBadRequestException.class, () -> service.updateOrderLine(request(OrderLineAction.SHIP), TransitionTrigger.ADMIN));
        assertEquals(0, line.getShippedQty());
    }
    @Test void shipmentCannotExceedAcceptedUnits() {
        line.setAcceptedQty(1); line.setOrderLineStatus(OrderLineStatus.PROCESSING);
        assertThrows(CustomBadRequestException.class, () -> service.updateOrderLine(request(OrderLineAction.SHIP), TransitionTrigger.ADMIN));
        assertEquals(0, line.getShippedQty());
    }
    @Test void failedAttemptCompletionIsNotCapturedMoney() {
        order.setPaymentStatus(PaymentStatus.FAILED); order.getPayment().setStatus(PaymentStatus.FAILED);
        assertFalse(MarketplaceFinanceService.paid(order));
    }
    @Test void verifiedCaptureCannotDisappearBecauseOfAGenericAttemptStatus() {
        order.getPayment().setCapturedAt(Instant.now());order.getPayment().setCapturedAmount(new BigDecimal("100.00"));
        order.setPaymentStatus(PaymentStatus.FAILED);order.getPayment().setStatus(PaymentStatus.FAILED);
        assertTrue(MarketplaceFinanceService.paid(order));
    }
}
