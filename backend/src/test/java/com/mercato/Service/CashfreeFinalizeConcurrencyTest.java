package com.mercato.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.Product;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Entity.fulfillment.OrderReservation;
import com.mercato.Entity.fulfillment.OrderStatus;
import com.mercato.Repository.CartReservationRepository;
import com.mercato.Repository.OrderRepository;
import com.mercato.Repository.OrderReservationRepository;
import com.mercato.Repository.PaymentRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.RefundRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Utils.AuthUtil;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashfreeFinalizeConcurrencyTest {

    private static final long LINE_ID = 1L;
    private static final String PRODUCT_ID = "P-100";
    private static final int ORDER_QTY = 2;

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderReservationRepository orderReservationRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartReservationRepository cartReservationRepository;
    @Mock private CartService cartService;
    @Mock private UserRepository userRepository;
    @Mock private AuthUtil authUtil;
    @Mock private EmailService emailService;
    @Mock private RestTemplate restTemplate;
    @Mock private RefundRepository refundRepository;

    private CashfreeServiceImpl cashfreeService;
    private Order order;
    private Product product;
    private final Object saveLock = new Object();
    private final AtomicReference<OrderReservation> storedOrderReservation = new AtomicReference<>();
    private final AtomicInteger emailSendCount = new AtomicInteger();
    private CountDownLatch threadsAtInsert;
    private CountDownLatch releaseInsert;

    @Test
    void alreadyConfirmedOrderDoesNotTouchTheCurrentCart() {
        order.setOrderStatus(OrderStatus.CONFIRMED);
        org.junit.jupiter.api.Assertions.assertFalse(cashfreeService.finalizeOrderAfterPaymentSuccess(order));
        org.mockito.Mockito.verifyNoInteractions(cartService);
    }

    @BeforeEach
    void setUp() {
        OrderReservationServiceImpl orderReservationService = new OrderReservationServiceImpl(
                orderReservationRepository,
                productRepository,
                cartReservationRepository
        );

        cashfreeService = new CashfreeServiceImpl(
                paymentRepository,
                orderRepository,
                orderReservationService,
                cartService,
                userRepository,
                authUtil,
                emailService,
                restTemplate,
                new ObjectMapper(),
                refundRepository
        );

        product = Product.builder()
                .productId(PRODUCT_ID)
                .physicalQty(10)
                .reservedQty(0)
                .build();

        OrderLine orderLine = new OrderLine();
        orderLine.setId(LINE_ID);
        orderLine.setProductId(PRODUCT_ID);
        orderLine.setOrderedQty(ORDER_QTY);

        order = new Order();
        order.setOrderId("ORD-CONCURRENT");
        order.setOrderStatus(OrderStatus.CREATED);
        order.setCustomerEmail("buyer@example.com");
        order.setCustomerName("Buyer");
        order.setTotalAmount(BigDecimal.valueOf(99));
        order.setOrderLines(List.of(orderLine));

        storedOrderReservation.set(null);
        emailSendCount.set(0);

        when(userRepository.findByEmail(order.getCustomerEmail())).thenReturn(Optional.empty());
        when(productRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderReservationRepository.findByOrderLine_Id(LINE_ID))
                .thenAnswer(inv -> Optional.ofNullable(storedOrderReservation.get()));
        when(orderReservationRepository.save(any(OrderReservation.class)))
                .thenAnswer(inv -> {
                    threadsAtInsert.countDown();
                    assertTrue(releaseInsert.await(10, TimeUnit.SECONDS),
                            "both threads must reach insert before either proceeds");

                    synchronized (saveLock) {
                        if (storedOrderReservation.get() != null) {
                            product.adjustReservedQty(-ORDER_QTY);
                            throw new DataIntegrityViolationException(
                                    "duplicate order line reservation",
                                    new ConstraintViolationException(
                                            "uk_order_reservation_order_line violated",
                                            null,
                                            "uk_order_reservation_order_line"
                                    )
                            );
                        }
                        OrderReservation reservation = inv.getArgument(0);
                        storedOrderReservation.set(reservation);
                        return reservation;
                    }
                });
        doAnswer(inv -> {
            emailSendCount.incrementAndGet();
            return null;
        }).when(emailService).sendOrderConfirmationEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void concurrentFinalize_createsOneReservationAndSendsOneEmail() throws Exception {
        threadsAtInsert = new CountDownLatch(2);
        releaseInsert = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        AtomicReference<Throwable> caught = new AtomicReference<>();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (cashfreeService.finalizeOrderAfterPaymentSuccess(order)) {
                        emailService.sendOrderConfirmationEmail(
                                order.getCustomerEmail(),
                                order.getCustomerName(),
                                order.getOrderId(),
                                order.getSubtotal(),
                                order.getCharges(),
                                order.getTotalAmount()
                        );
                    }
                } catch (Throwable t) {
                    caught.compareAndSet(null, t);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(threadsAtInsert.await(10, TimeUnit.SECONDS),
                "both threads must attempt the insert concurrently");
        releaseInsert.countDown();
        assertTrue(doneGate.await(10, TimeUnit.SECONDS), "concurrent finalize calls did not finish in time");
        executor.shutdown();

        assertNull(caught.get(), "finalize must not surface uncaught exceptions to callers");
        assertNotNull(storedOrderReservation.get(), "exactly one OrderReservation row should exist");
        assertEquals(1, emailSendCount.get(), "exactly one confirmation email should be sent");
        assertEquals(ORDER_QTY, product.getReservedQty(),
                "winner's reservation must remain; loser's increment must roll back");
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
    }
}
