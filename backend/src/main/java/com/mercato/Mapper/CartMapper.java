package com.mercato.Mapper;

import com.mercato.Entity.cart.Cart;
import com.mercato.Payloads.Response.CartChargeResponseDTO;
import com.mercato.Payloads.Response.CartItemResponseDTO;
import com.mercato.Payloads.Response.CartResponseDTO;

public class CartMapper {

    public static CartResponseDTO toDTO(Cart cart) {
        if (cart == null) return null;
        return new CartResponseDTO(
                cart.getVersion(),
                cart.getCartId(),
                cart.getSubtotal(),
                cart.getCharges().stream()
                        .map(charge -> new CartChargeResponseDTO(
                                charge.getType(),
                                charge.getAmount(),
                                charge.getDescription()
                        )).toList(),
                cart.getTotalCharges(),
                cart.getTotal(),
                cart.getCartItems().stream()
                        .map(cartItem -> new CartItemResponseDTO(
                                cartItem.getProduct().getProductId(),
                                cartItem.getQuantity(),
                                cartItem.getItemPrice(),
                                cartItem.getLineTotal(),
                                cartItem.getProduct().getAvailableQty() > 0,
                                cartItem.isOutOfStock()
                        )).toList(),
                null,
                cart.getDeliveryMethod() == null ? com.mercato.Entity.cart.DeliveryMethod.STANDARD : cart.getDeliveryMethod(),
                cart.getDeliveryOptions()
        );
    }
}
