package com.mercato.Service;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Repository.CartRepository;
import com.mercato.Repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderRetryHoldService {

    private final CartService cartService;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartReservationService cartReservationService;
    private final CartPricingService cartPricingService;

    @Transactional
    public void ensureCartHoldsForRetry(Order order, EcommUser user) {
        Cart cart = cartService.getCartByUser(user);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "userId", user.getUserId());
        }

        for (OrderLine orderLine : order.getOrderLines()) {
            Product product = productRepository.findByProductIdForUpdate(orderLine.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", orderLine.getProductId()));
            if (!product.isPurchasable()) throw new IllegalArgumentException("An item in this order is no longer approved or available");
            CartItem cartItem = cart.findItemByProductId(orderLine.getProductId()).orElse(null);
            if (cartItem == null) {
                cart.addProduct(product, orderLine.getOrderedQty());
                cartItem = cart.findItemByProductId(orderLine.getProductId())
                        .orElseThrow(() -> new IllegalStateException("Cart item missing after add"));
            } else if (cartItem.getQuantity() < orderLine.getOrderedQty()) {
                cart.updateProductQuantity(orderLine.getProductId(), orderLine.getOrderedQty());
            }

            cartReservationService.reserve(cartItem);
        }

        cartPricingService.applyCharges(cart);
        cartRepository.save(cart);
    }
}
