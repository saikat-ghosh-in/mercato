package com.mercato.Payloads.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderCancelRequestDTO {
    @NotBlank(message = "Order ID is required")
    private String orderId;
    private String reason;
    private String operationId;
    private Long expectedVersion;
}
