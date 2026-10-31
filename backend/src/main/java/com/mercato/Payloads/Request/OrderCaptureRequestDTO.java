package com.mercato.Payloads.Request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderCaptureRequestDTO {

    @NotNull(message = "addressId is required")
    private String addressId;
    @jakarta.validation.constraints.DecimalMin("0.00")
    private java.math.BigDecimal expectedTotal;
    private com.mercato.Entity.cart.DeliveryMethod deliveryMethod;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=120)
    private String operationId;
    @NotNull
    @jakarta.validation.constraints.Min(0)
    private Long expectedCartVersion;
}
