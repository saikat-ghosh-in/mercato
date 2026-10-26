package com.mercato.Service;

import com.mercato.Entity.Product;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.cart.CartReservation;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Entity.fulfillment.OrderLineAction;
import com.mercato.Entity.fulfillment.OrderReservation;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Repository.CartReservationRepository;
import com.mercato.Repository.OrderReservationRepository;
import com.mercato.Repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderReservationServiceImpl implements OrderReservationService {

    private final OrderReservationRepository orderReservationRepository;
    private final ProductRepository productRepository;
    private final CartReservationRepository cartReservationRepository;

    @Override
    @Transactional
    public void reserveForOrder(Order order) {
        order.getOrderLines().forEach(line -> reserveOrderLine(order, line));
    }

    @Override
    @Transactional
    public void transferCartReservationsToOrder(Order order, Cart cart) {
        finalizeInventoryForPaidOrder(order, cart);
    }

    @Override
    @Transactional
    public boolean finalizeInventoryForPaidOrder(Order order, Cart cart) {
        boolean owned = true;
        boolean anyNeededCreation = false;

        for (OrderLine orderLine : order.getOrderLines().stream().sorted(java.util.Comparator.comparing(OrderLine::getProductId)).toList()) {
            if (orderReservationRepository.findByOrderLine_Id(orderLine.getId()).isPresent()) {
                releaseOrphanedCartHold(cart, orderLine);
                continue;
            }

            anyNeededCreation = true;
            if (!createReservationForLine(order, orderLine, cart)) {
                owned = false;
            }
        }

        return anyNeededCreation && owned;
    }

    private boolean createReservationForLine(Order order, OrderLine orderLine, Cart cart) {
        CartReservation cartReservation = findCartReservation(cart, orderLine);
        // The parent order is locked by the allocator. A constraint error must roll back the
        // whole allocation; PostgreSQL cannot safely continue a transaction after one.
        if (cartReservation != null) transferSingleReservation(order, orderLine, cartReservation);
        else reserveOrderLine(order, orderLine);
        return true;
    }

    private static boolean isOrderLineReservationUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                if (constraintName != null && constraintName.contains("uk_order_reservation_order_line")) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null
                    && (message.contains("uk_order_reservation_order_line")
                    || message.contains("order_line_fk"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CartReservation findCartReservation(Cart cart, OrderLine orderLine) {
        if (cart == null) {
            return null;
        }
        return cart.findItemByProductId(orderLine.getProductId())
                .flatMap(cartItem -> cartReservationRepository
                        .findByCartItem_CartItemId(cartItem.getCartItemId()))
                .orElse(null);
    }

    /**
     * When poll reserved for the order but left the cart hold in place, remove the duplicate cart hold
     * without touching the existing order reservation.
     */
    private void releaseOrphanedCartHold(Cart cart, OrderLine orderLine) {
        CartReservation cartReservation = findCartReservation(cart, orderLine);
        if (cartReservation == null) {
            return;
        }

        Product product = productRepository.findByProductIdForUpdate(orderLine.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "productId", orderLine.getProductId()
                ));
        product.adjustReservedQty(-cartReservation.getReservedQty());
        productRepository.save(product);
        cartReservationRepository.delete(cartReservation);
        log.info("Released orphaned cart hold for product {} (order line {})",
                orderLine.getProductId(), orderLine.getId());
    }

    private void transferSingleReservation(Order order, OrderLine orderLine, CartReservation cartReservation) {
        Product product = productRepository.findByProductIdForUpdate(orderLine.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", orderLine.getProductId()));
        int held = cartReservation.getReservedQty();
        int ordered = orderLine.getOrderedQty();
        if (held > ordered) {
            cartReservation.updateReservedQuantity(held - ordered);
            cartReservationRepository.save(cartReservation);
        } else {
            if (held < ordered) {
                product.adjustReservedQty(ordered - held);
                productRepository.save(product);
            }
            cartReservationRepository.delete(cartReservation);
        }

        OrderReservation reservation = OrderReservation.builder()
                .order(order)
                .orderLine(orderLine)
                .product(product)
                .reservedQty(orderLine.getOrderedQty())
                .build();

        orderReservationRepository.save(reservation);
        log.info("Transferred cart reservation to order line {} for product {}",
                orderLine.getId(), orderLine.getProductId());
    }

    private void reserveOrderLine(Order order, OrderLine orderLine) {
        if (orderReservationRepository.findByOrderLine_Id(orderLine.getId()).isPresent()) {
            log.warn("Order reservation already exists for orderLine: {}", orderLine.getId());
            return;
        }

        Product product = productRepository.findByProductIdForUpdate(orderLine.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "productId", orderLine.getProductId()
                ));

        product.adjustReservedQty(orderLine.getOrderedQty());
        productRepository.save(product);

        OrderReservation reservation = OrderReservation.builder()
                .order(order)
                .orderLine(orderLine)
                .product(product)
                .reservedQty(orderLine.getOrderedQty())
                .build();

        orderReservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void settleQty(OrderLine orderLine, int qty, OrderLineAction action) {
        OrderReservation reservation = orderReservationRepository
                .findByOrderLine_Id(orderLine.getId())
                .orElse(null);

        Product product = productRepository.findByProductIdForUpdate(orderLine.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "productId", orderLine.getProductId()
                ));

        // Workflow counters have already advanced inside this transaction; reconstruct the
        // complete allocation immediately before this cancellation/shipment, not just this request.
        int outstandingBeforeMutation=orderLine.getPendingQty()+qty;
        if(reservation==null && orderLine.getOrder().getAllocationCompletedAt()!=null)
            throw new com.mercato.ExceptionHandler.CustomBadRequestException("Allocated line reservation requires reconciliation");
        if(reservation!=null && (reservation.getReservedQty()!=outstandingBeforeMutation
                || !reservation.getOrder().getOrderId().equals(orderLine.getOrder().getOrderId())
                || !reservation.getOrderLine().getId().equals(orderLine.getId())
                || !reservation.getProduct().getProductId().equals(orderLine.getProductId())))
            throw new com.mercato.ExceptionHandler.CustomBadRequestException("Reservation must match the full outstanding quantity and ownership");
        switch (action) {
            case SHIP -> {
                if(reservation==null || reservation.getReservedQty()<qty
                    || !reservation.getOrder().getOrderId().equals(orderLine.getOrder().getOrderId()))
                    throw new com.mercato.ExceptionHandler.CustomBadRequestException("A matching allocation is required before shipment");
                product.adjustInventory(-qty);
                if (reservation != null) {
                    product.adjustReservedQty(-qty);
                }
            }
            case CANCEL -> {
                if (reservation != null) {
                    product.adjustReservedQty(-qty);
                }
            }
            default -> throw new IllegalStateException(
                    "Cannot settle qty for action: " + action
            );
        }

        productRepository.save(product);

        if (reservation != null) {
            if (orderLine.isTerminal()) {
                orderLine.setOrderReservation(null);
                orderReservationRepository.delete(reservation);
            } else {
                reservation.setReservedQty(reservation.getReservedQty() - qty);
                orderReservationRepository.save(reservation);
            }
        }
    }
}
