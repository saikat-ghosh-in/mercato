package com.mercato.Payloads.Request;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
public class CartItemRequestDTO {
    @NotBlank
    @Size(max = 30)
    private String productId;
    @NotNull
    @Min(0)
    @Max(10000)
    private Integer quantity;
}
