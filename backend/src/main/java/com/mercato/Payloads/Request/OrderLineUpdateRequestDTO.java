package com.mercato.Payloads.Request;

import com.mercato.Entity.fulfillment.OrderLineAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderLineUpdateRequestDTO {

    @NotBlank(message = "Fulfillment ID is required")
    private String fulfillmentId;

    @NotNull(message = "Order line number is required")
    private Integer orderLineNumber;

    @NotNull(message = "Action is required")
    private OrderLineAction action;

    private Integer qty;

    private String reason;
    private String operationId;
    private Long expectedVersion;
}
