package com.mercato.inventory;

import com.mercato.Entity.Product;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.cart.CartReservation;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Entity.fulfillment.OrderReservation;
import com.mercato.Repository.CartReservationRepository;
import com.mercato.Repository.OrderReservationRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Service.OrderReservationServiceImpl;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderReservationServiceImplTest {

    private static final long LINE_ID = 1L;
    private static final long CART_ITEM_ID = 100L;
    private static final String PRODUCT_ID = "P-100";
    private static final int ORDER_QTY = 2;

    @Mock
    private OrderReservationRepository orderReservationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartReservationRepository cartReservationRepository;

    @InjectMocks
    private OrderReservationServiceImpl orderReservationService;

    private Product product;
    private Order order;
    private OrderLine orderLine;
    private Cart cart;
    private CartItem cartItem;
    private CartReservation cartReservation;
    private final AtomicReference<OrderReservation> storedOrderReservation = new AtomicReference<>();
    private final AtomicBoolean cartReservationDeleted = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .productId(PRODUCT_ID)
                .physicalQty(10)
                .reservedQty(ORDER_QTY)
                .build();

        cartItem = CartItem.builder()
                .cartItemId(CART_ITEM_ID)
                .product(product)
                .quantity(ORDER_QTY)
                .build();

        cart = new Cart();
        cart.setCartItems(List.of(cartItem));

        cartReservation = CartReservation.builder()
                .cartItem(cartItem)
                .product(product)
                .reservedQty(ORDER_QTY)
                .build();

        order = new Order();
        orderLine = new OrderLine();
        orderLine.setId(LINE_ID);
        orderLine.setProductId(PRODUCT_ID);
        orderLine.setOrderedQty(ORDER_QTY);
        // A transfer is valid only for the selected item generation, as captured at checkout.
        orderLine.setSourceCartItemId(CART_ITEM_ID);
        orderLine.setSourceSelectionGeneration(cartItem.getSelectionGeneration());
        order.addOrderLine(orderLine);

        cartReservationDeleted.set(false);
        storedOrderReservation.set(null);

        when(productRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderReservationRepository.findByOrderLine_Id(LINE_ID))
                .thenAnswer(inv -> Optional.ofNullable(storedOrderReservation.get()));
        when(orderReservationRepository.save(any(OrderReservation.class)))
                .thenAnswer(inv -> {
                    storedOrderReservation.set(inv.getArgument(0));
                    return storedOrderReservation.get();
                });
        when(cartReservationRepository.findByCartItem_CartItemId(CART_ITEM_ID))
                .thenAnswer(inv -> cartReservationDeleted.get()
                        ? Optional.empty()
                        : Optional.of(cartReservation));
        doAnswer(inv -> {
            cartReservationDeleted.set(true);
            return null;
        }).when(cartReservationRepository).delete(any(CartReservation.class));
    }

    @Test
    void transferKeepsAdditionalCartUnitsReserved() {
        product.setReservedQty(5);
        cartReservation.setReservedQty(5);
        cartItem.setQuantity(5);

        orderReservationService.finalizeInventoryForPaidOrder(order, cart);

        assertEquals(5, product.getReservedQty());
        assertEquals(3, cartReservation.getReservedQty());
        assertEquals(2, storedOrderReservation.get().getReservedQty());
        verify(cartReservationRepository, never()).delete(cartReservation);
    }

    @Test
    void transferReservesTheDifferenceWhenCartWasReducedDuringPayment() {
        product.setReservedQty(1);
        cartReservation.setReservedQty(1);
        cartItem.setQuantity(1);

        orderReservationService.finalizeInventoryForPaidOrder(order, cart);

        assertEquals(2, product.getReservedQty());
        assertEquals(2, storedOrderReservation.get().getReservedQty());
    }

    @Test
    void reserveForOrder_incrementsReservedQtyAndPersistsReservation() {
        product.setReservedQty(1);

        orderReservationService.reserveForOrder(order);

        assertEquals(3, product.getReservedQty());
        verify(productRepository).save(product);
        assertEquals(ORDER_QTY, storedOrderReservation.get().getReservedQty());
    }

    @Test
    void reserveForOrder_skipsWhenReservationAlreadyExists() {
        storedOrderReservation.set(OrderReservation.builder().build());
        product.setReservedQty(5);

        orderReservationService.reserveForOrder(order);

        verify(productRepository, never()).findByProductIdForUpdate(any());
        verify(orderReservationRepository, never()).save(any());
        assertEquals(5, product.getReservedQty());
    }

    @Test
    void pollThenWebhook_createsOneOrderReservationWithoutDoubleCountingReservedQty() {
        // Poll path (legacy): reserveForOrder while cart hold still exists
        orderReservationService.reserveForOrder(order);
        assertEquals(ORDER_QTY * 2, product.getReservedQty(), "poll double-counts cart + order hold");

        // Webhook path: finalize reconciles orphaned cart hold
        orderReservationService.finalizeInventoryForPaidOrder(order, cart);

        assertEquals(ORDER_QTY, product.getReservedQty(),
                "orphaned cart hold released; only order reservation remains");
        assertTrue(cartReservationDeleted.get());
        verify(orderReservationRepository, times(1)).save(any(OrderReservation.class));
    }

    @Test
    void webhookThenPoll_transferOnceAndIdempotentOnSecondFinalize() {
        product.setReservedQty(ORDER_QTY);

        orderReservationService.finalizeInventoryForPaidOrder(order, cart);
        assertEquals(ORDER_QTY, product.getReservedQty());
        assertTrue(cartReservationDeleted.get());
        verify(orderReservationRepository, times(1)).save(any(OrderReservation.class));

        // Duplicate poll/webhook delivery
        orderReservationService.finalizeInventoryForPaidOrder(order, cart);

        assertEquals(ORDER_QTY, product.getReservedQty(), "second finalize must not change reservedQty");
        verify(orderReservationRepository, times(1)).save(any(OrderReservation.class));
        verify(productRepository, never()).save(product);
    }

    @Test
    void finalizeInventory_transfersCartHoldWithoutChangingReservedQty() {
        ArgumentCaptor<OrderReservation> captor = ArgumentCaptor.forClass(OrderReservation.class);

        orderReservationService.finalizeInventoryForPaidOrder(order, cart);

        verify(orderReservationRepository).save(captor.capture());
        assertEquals(ORDER_QTY, captor.getValue().getReservedQty());
        assertEquals(ORDER_QTY, product.getReservedQty(), "transfer moves hold without net reservedQty change");
        assertTrue(cartReservationDeleted.get());
    }

    @Test
    void uniqueConstraintFailureRollsBackInsteadOfContinuingAnAbortedPostgresTransaction() {
        AtomicBoolean peerCommitted = new AtomicBoolean(false);

        when(orderReservationRepository.findByOrderLine_Id(LINE_ID))
                .thenAnswer(inv -> peerCommitted.get()
                        ? Optional.of(OrderReservation.builder().build())
                        : Optional.empty());
        when(orderReservationRepository.save(any(OrderReservation.class)))
                .thenAnswer(inv -> {
                    peerCommitted.set(true);
                    throw new DataIntegrityViolationException(
                            "duplicate order line reservation",
                            new ConstraintViolationException(
                                    "uk_order_reservation_order_line violated",
                                    null,
                                    "uk_order_reservation_order_line"
                            )
                    );
                });

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
            () -> orderReservationService.finalizeInventoryForPaidOrder(order, cart));
        verify(orderReservationRepository, times(1)).save(any(OrderReservation.class));
    }
}
