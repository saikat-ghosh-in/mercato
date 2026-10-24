package com.mercato.fulfillment;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.Repository.OrderRepository;
import com.mercato.Service.EmailService;
import com.mercato.Service.ShipmentNotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentNotificationServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private com.mercato.Jobs.DurableJobs jobs;

    @InjectMocks
    private ShipmentNotificationServiceImpl shipmentNotificationService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setOrderId("ORD-1");
        order.setCustomerEmail("buyer@example.com");
        order.setCustomerName("Buyer");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void notifyShippedIfNeeded_sendsOnceAcrossRepeatedCalls() {
        assertTrue(shipmentNotificationService.notifyShippedIfNeeded(order));
        assertNotNull(order.getShipmentEmailQueuedAt());

        assertFalse(shipmentNotificationService.notifyShippedIfNeeded(order));

        verify(jobs,times(1)).enqueue(eq("SHIPMENT_EMAIL"),eq("shipment-email:ORD-1"),any());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void notifyShippedIfNeeded_skipsWhenAlreadySent() {
        order.setShipmentEmailSentAt(java.time.Instant.now());

        assertFalse(shipmentNotificationService.notifyShippedIfNeeded(order));

        org.mockito.Mockito.verifyNoInteractions(jobs);
        verify(orderRepository, never()).save(any());
    }
}
