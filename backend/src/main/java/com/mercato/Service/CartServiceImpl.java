package com.mercato.Service;

import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.CartMapper;
import com.mercato.Payloads.Request.CartItemRequestDTO;
import com.mercato.Payloads.Request.CartRequestDTO;
import com.mercato.Payloads.Response.CartResponseDTO;
import com.mercato.Repository.CartRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Utils.CartContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartPricingService cartPricingService;
    private final UserRepository userRepository;
    private final CartReservationService cartReservationService;

    @Override
    @Transactional
    public CartResponseDTO addProductToCart(CartRequestDTO cartRequestDTO, CartContext context) {
        Cart cart = resolveCart(context);

        cartRequestDTO.getCartItems().forEach(dto -> {
            String productId = dto.getProductId();
            Integer requestedQuantity = dto.getQuantity();
            if (requestedQuantity == null || requestedQuantity <= 0)
                throw new IllegalArgumentException("Quantity must be greater than 0");

            Product product = productRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product", "productId", productId
                    ));
            if (!product.isActive())
                throw new IllegalArgumentException("This product is no longer available");
            cart.addProduct(product, requestedQuantity);

            CartItem cartItem = cart.findItemByProductId(productId)
                    .orElseThrow(() -> new IllegalStateException("CartItem not found after add"));
            cartReservationService.reserve(cartItem);
        });

        cartPricingService.applyCharges(cart);
        cartRepository.save(cart);
        return CartMapper.toDTO(cart);
    }

    @Override
    @Transactional
    public CartResponseDTO getCart(CartContext context) {
        Cart cart = resolveCart(context);
        cartPricingService.applyCharges(cart);
        return CartMapper.toDTO(cart);
    }

    @Override
    @Transactional
    public CartResponseDTO updateProductQuantityInCart(CartItemRequestDTO dto, CartContext context) {
        if (dto.getQuantity() == null || dto.getQuantity() < 0)
            throw new IllegalArgumentException("Quantity cannot be less than 0");

        Cart cart = resolveCart(context);
        CartItem cartItem = cart.findItemByProductId(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", dto.getProductId()));
        String warning = null;

        if (dto.getQuantity() == 0) {
            cartReservationService.release(cartItem);
            cart.removeCartItem(cartItem);
        } else {
            Product product = productRepository.findByProductIdForUpdate(dto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", dto.getProductId()));

            int delta = dto.getQuantity() - cartItem.getQuantity();
            int effectiveDelta = Math.min(delta, product.getAvailableQty());
            int effectiveQty = cartItem.getQuantity() + effectiveDelta;

            if (effectiveQty < dto.getQuantity()) {
                warning = String.format(
                        "Only %d unit%s available — your cart has been updated",
                        effectiveQty, effectiveQty != 1 ? "s" : ""
                );
            }

            cart.updateProductQuantity(dto.getProductId(), effectiveQty);
            cartReservationService.reserve(cartItem);
        }

        cartPricingService.applyCharges(cart);
        cartRepository.save(cart);

        CartResponseDTO response = CartMapper.toDTO(cart);
        response.setWarning(warning);
        return response;
    }

    @Override
    @Transactional
    public void deleteProductFromCart(String productId, CartContext context) {
        Cart cart = resolveCart(context);
        CartItem cartItem = cart.findItemByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        cartReservationService.release(cartItem);
        cart.removeCartItem(cartItem);
        cartPricingService.applyCharges(cart);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void clearCart(CartContext context) {
        Cart cart = resolveCart(context);
        if (cart.isEmpty()) return;

        List<CartItem> itemsToRelease = List.copyOf(cart.getCartItems());
        itemsToRelease.forEach(cartReservationService::release);
        cart.clear();
        cartPricingService.applyCharges(cart);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void removePurchasedItems(Cart cart, com.mercato.Entity.fulfillment.Order order) {
        for (var line : order.getOrderLines()) {
            cart.findItemByProductId(line.getProductId()).ifPresent(item -> {
                int remaining = Math.max(0, item.getQuantity() - line.getOrderedQty());
                if (remaining == 0) {
                    cartReservationService.release(item);
                    cart.removeCartItem(item);
                } else {
                    item.updateQuantity(remaining);
                    cartReservationService.reserve(item);
                }
            });
        }
        cartPricingService.applyCharges(cart);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void mergeGuestCartOnLogin(String userId, String guestToken) {
        Optional<Cart> guestCartOpt =
                cartRepository.findByGuestToken(guestToken);
        if (guestCartOpt.isEmpty()) return;

        Cart guestCart = guestCartOpt.get();
        if (guestCart.isEmpty()) {
            cartRepository.delete(guestCart);
            return;
        }

        Cart userCart = resolveCart(new CartContext(userId, null));

        // Release guest holds before reserving the merged cart. Both operations are in
        // this transaction, so a failed merge restores the original guest holds.
        cartReservationService.releaseAllForCart(guestCart);

        for (CartItem guestItem : guestCart.getCartItems()) {
            Product product = productRepository.findByProductIdForUpdate(guestItem.getProduct().getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", guestItem.getProduct().getProductId()));
            if (!product.isActive()) continue;
            userCart.findItemByProductId(product.getProductId())
                    .ifPresentOrElse(
                            existing -> {
                                int mergedQty = Math.max(existing.getQuantity(), guestItem.getQuantity());
                                existing.updateQuantity(mergedQty);
                                cartReservationService.reserve(existing);
                            },
                            () -> {
                                userCart.addProduct(product, guestItem.getQuantity());
                                CartItem newItem = userCart.findItemByProductId(product.getProductId())
                                        .orElseThrow();
                                cartReservationService.reserve(newItem);
                            }
                    );
        }

        cartPricingService.applyCharges(userCart);
        cartRepository.save(userCart);
        cartRepository.delete(guestCart);
    }

    @Override
    @Transactional(readOnly = true)
    public Cart getCartByUser(EcommUser user) {
        Cart cart = cartRepository.findByUser_UserId(user.getUserId()).orElse(null);
        if (cart == null) return null;
        cartPricingService.applyCharges(cart);
        return cart;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartResponseDTO> getAllCarts() {
        return cartRepository.findAll().stream()
                .map(CartMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDTO getCartById(String cartId) {
        if (cartId == null) throw new IllegalArgumentException("cartId must not be null!");
        Cart cart = cartRepository.findByCartId(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));
        return CartMapper.toDTO(cart);
    }

    private Cart resolveCart(CartContext context) {
        if (!context.isGuest()) {
            return cartRepository.findByUser_UserId(context.userId())
                    .orElseGet(() -> createUserCart(context.userId()));
        }
        if (context.guestToken() != null) {
            return cartRepository.findByGuestToken(context.guestToken())
                    .orElseGet(() -> createGuestCart(context.guestToken()));
        }
        throw new IllegalStateException("Cannot resolve cart: no userId or guestToken");
    }

    private Cart createUserCart(String userId) {
        EcommUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        Cart cart = Cart.builder()
                .user(user)
                .build();
        return cartRepository.save(cart);
    }

    private Cart createGuestCart(String guestToken) {
        Cart cart = Cart.builder()
                .guestToken(guestToken)
                .build();
        return cartRepository.save(cart);
    }
}
