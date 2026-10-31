package com.mercato.Payloads.Response;

import com.mercato.Entity.fulfillment.OrderStatus;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@lombok.RequiredArgsConstructor
public class OrderSummaryDTO {
    @lombok.Setter
    private com.mercato.Service.FulfillmentWorkflow.Summary workflow;
    private final String orderId;
    private final OrderStatus orderStatus;
    private final PaymentStatus paymentStatus;
    private final BigDecimal totalAmount;
    private final Instant createdAt;
}
