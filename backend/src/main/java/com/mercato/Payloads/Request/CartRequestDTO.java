package com.mercato.Payloads.Request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class CartRequestDTO {
    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<@NotNull CartItemRequestDTO> cartItems;
}
