package com.mercato.Payloads.Response;

import com.mercato.Entity.fulfillment.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class AdminOrderSummaryDTO {
    @lombok.Setter
    private com.mercato.Service.FulfillmentWorkflow.Summary workflow;
    private final String orderId;
    private final Customer customer;
    private final OrderStatus orderStatus;
    private final BigDecimal totalAmount;
    private final Instant createdAt;

    public AdminOrderSummaryDTO(String orderId, String name, String email,
                                OrderStatus orderStatus, BigDecimal totalAmount,
                                Instant createdAt) {
        this.orderId = orderId;
        this.customer = new Customer(name, email);
        this.orderStatus = orderStatus;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    @Getter
    @AllArgsConstructor
    public static class Customer {
        private final String name;
        private final String email;
    }
}
