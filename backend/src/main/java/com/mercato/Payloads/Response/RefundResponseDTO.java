package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@lombok.NoArgsConstructor(force=true)
@AllArgsConstructor
public class RefundResponseDTO {
    private final String refundId;
    private final String gatewayReference;
    private final BigDecimal amount;
    private final String currency;
    private final String status;
    private final String reason;
    private final String gatewayResponseMessage;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;
}