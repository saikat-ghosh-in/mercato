package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@lombok.NoArgsConstructor(force=true)
@AllArgsConstructor
public class PaymentResponseDTO {
    private String paymentId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private String gatewayReference;
    private String gatewayName;
    private String gatewayResponseMessage;
    private String cfOrderId;
    private Instant initiatedAt;
    private Instant completedAt;
}
