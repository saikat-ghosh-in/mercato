package com.mercato.order;

import com.mercato.Entity.Address;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.payment.Payment;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import com.mercato.Payloads.Request.OrderCaptureRequestDTO;
import com.mercato.Payloads.Response.CashfreeOrderResponse;
import com.mercato.Payloads.Response.OrderPlacementResponseDTO;
import com.mercato.Repository.AddressRepository;
import com.mercato.Repository.OrderRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Service.*;
import com.mercato.Utils.AuthUtil;
import com.mercato.Utils.CartContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServicePlaceOrderTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartService cartService;
    @Mock private CashfreeService cashfreeService;
    @Mock private AddressRepository addressRepository;
    @Mock private AuthUtil authUtil;
    @Mock private CartReservationService cartReservationService;
    @Mock private ProductRepository productRepository;
    @Mock private OrderLineUpdateService orderLineUpdateService;
    @Mock private CommissionService commissions;
    @Mock private com.mercato.Repository.SellerApplicationRepository sellerApplications;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void expiredHoldDoesNotCreateAGatewayOrder() {
        EcommUser user = new EcommUser();
        Product product = Product.builder().productId("P-1").build();
        CartItem item = CartItem.builder().product(product).quantity(1).build();
        Cart cart = new Cart();
        cart.setCartItems(List.of(item));
        when(authUtil.getLoggedInUser()).thenReturn(user);
        when(cartService.getCartByUser(user)).thenReturn(cart);
        doThrow(new com.mercato.ExceptionHandler.InsufficientInventoryException("P-1", 0))
                .when(cartReservationService).validateHeld(item);

        org.junit.jupiter.api.Assertions.assertThrows(com.mercato.ExceptionHandler.InsufficientInventoryException.class,
                () -> orderService.placeOrder(new OrderCaptureRequestDTO()));

        verifyNoInteractions(cashfreeService, addressRepository, orderRepository);
    }

    @Test
    void placeOrder_doesNotClearCartOrReleaseReservationsBeforePayment() {
        EcommUser user = new EcommUser();
        user.setUserId("U-1");
        user.setEmail("buyer@test.com");
        user.setUsername("buyer");

        Product product = Product.builder()
                .productId("P-1")
                .productName("Widget")
                .physicalQty(10)
                .reservedQty(2)
                .sellingPrice(BigDecimal.valueOf(100))
                .build();

        EcommUser seller = new EcommUser();
        seller.setEmail("seller@test.com");
        seller.setSellerDisplayName("Seller");
        product.setSeller(seller);

        CartItem cartItem = CartItem.builder()
                .cartItemId(11L)
                .product(product)
                .quantity(2)
                .build();

        Cart cart = new Cart();
        cart.setCartItems(List.of(cartItem));
        cartItem.setSellerDeliveryCharge(new BigDecimal("250.00"));

        Address address = new Address();
        address.setAddressId("ADR-1");
        address.setUser(user);

        OrderCaptureRequestDTO request = new OrderCaptureRequestDTO();
        request.setAddressId("ADR-1");

        when(authUtil.getLoggedInUser()).thenReturn(user);
        when(cartService.getCartByUser(user)).thenReturn(cart);
        when(addressRepository.findByAddressIdAndUser_UserId("ADR-1", "U-1"))
                .thenReturn(Optional.of(address));
        when(cashfreeService.createOrder(any(Order.class), eq(user)))
                .thenReturn(new CashfreeOrderResponse("CF-1", "SESSION-1"));
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            Payment payment = new Payment();
            payment.setPaymentSessionId("SESSION-1");
            payment.setStatus(PaymentStatus.INITIATED);
            payment.setPaymentMethod(com.mercato.Entity.fulfillment.payment.PaymentMethod.UNKNOWN);
            payment.setAmount(order.getTotalAmount());
            payment.setCurrency("INR");
            order.attachPayment(payment);
            return null;
        }).when(cashfreeService).initiatePayment(any(Order.class), any(), any());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        when(commissions.currentRate()).thenReturn(new BigDecimal("0.50"));
        OrderPlacementResponseDTO response = orderService.placeOrder(request);

        assertNotNull(response.getPaymentSessionId());
        var captured = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captured.capture());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("0.50"), captured.getValue().getOrderLines().get(0).getCommissionRatePercent());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("500.00"), captured.getValue().getOrderLines().get(0).getCommissionCapPerItem());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("250.00"), captured.getValue().getOrderLines().get(0).getSellerDeliveryCharge());
        verify(cartReservationService, never()).release(any(CartItem.class));
        verify(cartService, never()).clearCart(any(CartContext.class));
        verify(cashfreeService).initiatePayment(any(Order.class), any(), any());
    }

    @Test
    void changedQuoteCannotCreateAPayment() {
        EcommUser user = new EcommUser();
        Product product = Product.builder().productId("P-QUOTE").sellingPrice(BigDecimal.valueOf(100)).build();
        Cart cart = new Cart(); cart.setCartItems(List.of(CartItem.builder().product(product).quantity(1).unitPrice(BigDecimal.valueOf(100)).build()));
        when(authUtil.getLoggedInUser()).thenReturn(user); when(cartService.getCartByUser(user)).thenReturn(cart);
        OrderCaptureRequestDTO request = new OrderCaptureRequestDTO(); request.setExpectedTotal(BigDecimal.ONE);
        org.junit.jupiter.api.Assertions.assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class, () -> orderService.placeOrder(request));
        request.setExpectedTotal(cart.getTotal()); request.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);
        org.junit.jupiter.api.Assertions.assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class, () -> orderService.placeOrder(request));
        verifyNoInteractions(cashfreeService);
    }

    @Test
    void enrolledUnregisteredSellerCannotShipOutsidePickupState() {
        EcommUser user = new EcommUser(); user.setUserId("U-STATE");
        EcommUser seller = new EcommUser(); seller.setUserId("S-STATE");
        Product product = Product.builder().productId("P-STATE").sellingPrice(BigDecimal.valueOf(100)).seller(seller).build();
        Cart cart = new Cart(); cart.setCartItems(List.of(CartItem.builder().product(product).quantity(1).unitPrice(BigDecimal.valueOf(100)).build()));
        Address address = new Address(); address.setAddressId("A-STATE"); address.setState("Maharashtra");
        var application = new com.mercato.Entity.SellerApplication(); application.setTaxRegistrationType("ENROLLED_UNREGISTERED"); application.setPickupState("Karnataka");
        when(authUtil.getLoggedInUser()).thenReturn(user); when(cartService.getCartByUser(user)).thenReturn(cart);
        when(addressRepository.findByAddressIdAndUser_UserId("A-STATE", "U-STATE")).thenReturn(Optional.of(address));
        when(commissions.currentRate()).thenReturn(new BigDecimal("5.00"));
        when(sellerApplications.findByUser_UserId("S-STATE")).thenReturn(Optional.of(application));
        OrderCaptureRequestDTO request = new OrderCaptureRequestDTO(); request.setAddressId("A-STATE");
        org.junit.jupiter.api.Assertions.assertThrows(com.mercato.ExceptionHandler.CustomBadRequestException.class, () -> orderService.placeOrder(request));
        verifyNoInteractions(cashfreeService);
    }
}
