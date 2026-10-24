package com.mercato.Service;

import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.EcommUser;
import com.mercato.Payloads.Request.CartItemRequestDTO;
import com.mercato.Payloads.Request.CartRequestDTO;
import com.mercato.Payloads.Response.CartResponseDTO;
import com.mercato.Utils.CartContext;

import java.util.List;

public interface CartService {

    CartResponseDTO addProductToCart(CartRequestDTO cartRequestDTO, CartContext context);

    CartResponseDTO getCart(CartContext context);

    CartResponseDTO selectDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod method, CartContext context);

    CartResponseDTO updateProductQuantityInCart(CartItemRequestDTO cartItemRequestDTO, CartContext context);

    void deleteProductFromCart(String productId, CartContext context);

    void clearCart(CartContext context);

    void removePurchasedItems(Cart cart, com.mercato.Entity.fulfillment.Order order);

    Cart getCartByUser(EcommUser user);

    List<CartResponseDTO> getAllCarts();

    CartResponseDTO getCartById(String cartId);

    void mergeGuestCartOnLogin(String userId, String guestToken);
}
