package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@lombok.RequiredArgsConstructor
public class FulfillmentOrderResponseDTO {
    @lombok.Setter
    private com.mercato.Service.FulfillmentWorkflow.Summary workflow;
    private final String fulfillmentId;
    private final String orderId;
    private final Customer customer;
    private final DeliveryAddress deliveryAddress;
    private final List<OrderLineResponseDTO> orderLines;
    private final Instant createdAt;

    @Getter
    @AllArgsConstructor
    public static class Customer {
        private final String name;
        private final String email;
    }

    @Getter
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
