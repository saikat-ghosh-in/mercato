package com.mercato.Service;

import com.mercato.Entity.cart.CartReservation;
import com.mercato.Entity.Product;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.ExceptionHandler.InsufficientInventoryException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Repository.CartReservationRepository;
import com.mercato.Repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartReservationServiceImpl implements CartReservationService {

    @Value("${cart.item.reservation.minutes}")
    private int cartReservationMinutes;

    private final CartReservationRepository cartReservationRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void reserve(@NonNull CartItem cartItem) {
        Product product = getProductForUpdate(cartItem.getProduct().getProductId());
        if (!product.isPurchasable()) throw new IllegalArgumentException("This listing is no longer approved or available");
        int requestedQty = cartItem.getQuantity();

        CartReservation reservation = cartReservationRepository
                .findByCartItem_CartItemId(cartItem.getCartItemId())
                .map(existing -> {
                    int delta = requestedQty - existing.getReservedQty();
                    product.adjustReservedQty(delta);
                    existing.updateReservedQuantity(requestedQty);
                    existing.extendExpiry(cartReservationMinutes);
                    return existing;
                })
                .orElseGet(() -> {
                    product.adjustReservedQty(requestedQty);
                    return CartReservation.builder()
                            .cartItem(cartItem)
                            .product(product)
                            .reservedQty(requestedQty)
                            .build();
                });

        reservation.extendExpiry(cartReservationMinutes);
        productRepository.save(product);
        cartReservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void release(@NonNull CartItem cartItem) {
        cartReservationRepository.findByCartItem_CartItemId(cartItem.getCartItemId())
                .ifPresent(cartReservation -> {
                    Product product = getProductForUpdate(cartReservation.getProduct().getProductId());
                    product.adjustReservedQty(-cartReservation.getReservedQty());
                    productRepository.save(product);
                    cartReservationRepository.delete(cartReservation);
                });
    }

    @Override
    @Transactional
    public void releaseAllForCart(@NonNull Cart cart) {
        List<Long> cartItemIds = cart.getCartItems().stream()
                .map(CartItem::getCartItemId)
                .toList();

        if (cartItemIds.isEmpty()) return;

        List<CartReservation> cartReservations =
                cartReservationRepository.findAllByCartItemIds(cartItemIds);

        cartReservations.forEach(cartReservation -> {
            Product product = getProductForUpdate(cartReservation.getProduct().getProductId());
            product.adjustReservedQty(-cartReservation.getReservedQty());
            productRepository.save(product);
        });

        cartReservationRepository.deleteAll(cartReservations);
    }

    @Override
    @Transactional
    public void releaseExpired() {
        var expired = cartReservationRepository.findExpiredCandidates(Instant.now());

        if (expired.isEmpty()) return;

        for (var candidate : expired) {
            Product product = getProductForUpdate(candidate.getProductId());
            var current = cartReservationRepository.findById(candidate.getId());
            if (current.isEmpty() || current.get().getExpiresAt().isAfter(Instant.now())) continue;
            CartReservation cartReservation = current.get();
            CartItem cartItem = cartReservation.getCartItem();

            product.adjustReservedQty(-cartReservation.getReservedQty());
            productRepository.save(product);

            if (product.getAvailableQty() == 0) {
                cartItem.setOutOfStock(true);
            }

            cartReservationRepository.delete(cartReservation);
        }
    }

    @Override
    @Transactional
    public void validateHeld(@NonNull CartItem cartItem) {
        if (!getProductForUpdate(cartItem.getProduct().getProductId()).isPurchasable())
            throw new IllegalArgumentException("This listing is no longer approved or available");
        cartReservationRepository.findByCartItem_CartItemId(cartItem.getCartItemId())
                .filter(reservation -> reservation.getExpiresAt() != null && reservation.getExpiresAt().isAfter(Instant.now()))
                .filter(reservation -> reservation.getReservedQty() >= cartItem.getQuantity())
                .orElseThrow(() -> new InsufficientInventoryException(
                        cartItem.getProduct().getProductId(),
                        cartItem.getProduct().getAvailableQty()
                ));
    }

    private Product getProductForUpdate(String productId) {
        return productRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "productId", productId
                ));
    }
}
