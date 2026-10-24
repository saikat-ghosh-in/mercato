package com.mercato.Service;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ShipmentNotificationServiceImpl implements ShipmentNotificationService {

    private final OrderRepository orderRepository;
    private final com.mercato.Jobs.DurableJobs jobs;

    @Override
    @Transactional
    public boolean notifyShippedIfNeeded(Order order) {
        if (order.getShipmentEmailSentAt() != null || order.getShipmentEmailQueuedAt()!=null) {
            return false;
        }

        jobs.enqueue("SHIPMENT_EMAIL","shipment-email:"+order.getOrderId(),java.util.Map.of("orderId",order.getOrderId(),"to",order.getCustomerEmail()));
        order.setShipmentEmailQueuedAt(Instant.now());
        orderRepository.save(order);
        return true;
    }
}
