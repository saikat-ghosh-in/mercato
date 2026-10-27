package com.mercato.Payloads.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CartResponseDTO {
    private long version;
    private final String cartId;
    private final BigDecimal subtotal;
    private final List<CartChargeResponseDTO> charges;
    private final BigDecimal totalCharges;
    private final BigDecimal total;
    private final List<CartItemResponseDTO> cartItems;
    private String warning;
    private com.mercato.Entity.cart.DeliveryMethod deliveryMethod;
    private List<DeliveryOptionDTO> deliveryOptions;
}
