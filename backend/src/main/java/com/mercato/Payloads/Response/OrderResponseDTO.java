package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@lombok.NoArgsConstructor(force=true)
@lombok.RequiredArgsConstructor
public class OrderResponseDTO {
    @lombok.Setter
    private com.mercato.Service.FulfillmentWorkflow.Summary workflow;
    private final String orderId;
    private final String orderStatus;

    private final Customer customer;
    private final DeliveryAddress deliveryAddress;
    private final PaymentResponseDTO paymentSummary;
    private final RefundResponseDTO refundSummary;

    private final List<OrderLineResponseDTO> orderLines;

    private final String currency;
    private final BigDecimal subTotal;
    private final BigDecimal charges;
    private final BigDecimal totalAmount;
    private final boolean inventoryFinalizationFailed;

    private final Instant createdAt;
    private final Instant updatedAt;
    private final com.mercato.Entity.cart.DeliveryMethod deliveryMethod;
    private final String deliveryEstimate;


    @Getter
@lombok.NoArgsConstructor(force=true)
    @AllArgsConstructor
    public static class Customer {
        private final String name;
        private final String email;
    }

    @Getter
@lombok.NoArgsConstructor(force=true)
    @AllArgsConstructor
    public static class DeliveryAddress {
        private final String recipientName;
        private final String recipientPhone;
        private final String addressLine1;
        private final String addressLine2;
        private final String city;
        private final String state;
        private final String pincode;
    }
}
