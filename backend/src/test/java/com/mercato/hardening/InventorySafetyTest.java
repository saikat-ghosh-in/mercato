package com.mercato.hardening;

import com.mercato.Entity.*;
import com.mercato.Entity.cart.*;
import com.mercato.Entity.fulfillment.OrderReservation;
import com.mercato.Mapper.ProductMapper;
import com.mercato.Payloads.Request.*;
import com.mercato.Repository.*;
import com.mercato.Service.*;
import com.mercato.Utils.AuthUtil;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventorySafetyTest {
    @Test
    void completingPurchasePreservesUnrelatedItemsAndAdditionalUnits() {
        CartRepository carts = mock(CartRepository.class);
        CartReservationService reservations = mock(CartReservationService.class);
        CartServiceImpl service = new CartServiceImpl(carts, mock(ProductRepository.class), mock(CartPricingService.class), mock(UserRepository.class), reservations);
        Product purchased = Product.builder().productId("purchased").physicalQty(10).sellingPrice(BigDecimal.TEN).build();
        Product other = Product.builder().productId("other").physicalQty(10).sellingPrice(BigDecimal.ONE).build();
        Cart cart = new Cart();
        cart.addProduct(purchased, 5);
        cart.addProduct(other, 1);
        var order = new com.mercato.Entity.fulfillment.Order();
        var line = new com.mercato.Entity.fulfillment.OrderLine();
        line.setProductId("purchased");
        line.setOrderedQty(2);
        order.setOrderLines(List.of(line));

        service.removePurchasedItems(cart, order);

        assertEquals(3, cart.findItemByProductId("purchased").orElseThrow().getQuantity());
        assertEquals(1, cart.findItemByProductId("other").orElseThrow().getQuantity());
        verify(reservations).reserve(cart.findItemByProductId("purchased").orElseThrow());
        verify(reservations, never()).release(any());
    }

    @Test
    void mergingGuestCartReleasesItsHoldBeforeReservingTheLastUnits() {
        CartRepository carts = mock(CartRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        CartReservationService reservations = mock(CartReservationService.class);
        CartServiceImpl service = new CartServiceImpl(carts, products, mock(CartPricingService.class), mock(UserRepository.class), reservations);
        Product product = Product.builder().productId("P-1").physicalQty(2).sellingPrice(BigDecimal.TEN).build();
        Cart guest = new Cart();
        guest.addProduct(product, 2);
        product.setReservedQty(2);
        Cart user = new Cart();
        when(carts.findByGuestToken("guest")).thenReturn(Optional.of(guest));
        when(carts.findByUser_UserId("user")).thenReturn(Optional.of(user));
        when(products.findByProductIdForUpdate("P-1")).thenReturn(Optional.of(product));
        doAnswer(i -> { product.adjustReservedQty(-2); return null; }).when(reservations).releaseAllForCart(guest);
        doAnswer(i -> { product.adjustReservedQty(((CartItem)i.getArgument(0)).getQuantity()); return null; }).when(reservations).reserve(any());

        service.mergeGuestCartOnLogin("user", "guest");

        assertEquals(2, user.getCartQuantity());
        assertEquals(2, product.getReservedQty());
        var ordered = inOrder(reservations);
        ordered.verify(reservations).releaseAllForCart(guest);
        ordered.verify(reservations).reserve(any());
        verify(carts).delete(guest);
    }

    @Test
    void stockCorrectionCannotDiscardPaidOrderReservations() {
        ProductRepository products = mock(ProductRepository.class);
        OrderReservationRepository paid = mock(OrderReservationRepository.class);
        CartReservationRepository carts = mock(CartReservationRepository.class);
        AuthUtil auth = mock(AuthUtil.class);
        ProductServiceImpl service = new ProductServiceImpl(products, mock(CategoryRepository.class), auth, new ProductMapper(), paid, carts, mock(FileService.class));
        EcommUser admin = mock(EcommUser.class);
        when(admin.isAdmin()).thenReturn(true);
        when(auth.getLoggedInUser()).thenReturn(admin);
        Product product = Product.builder().productId("P-1").physicalQty(4).reservedQty(3).build();
        when(products.findByProductIdForUpdate("P-1")).thenReturn(Optional.of(product));
        when(paid.findAllByProductIdOrderByCreatedAtDesc("P-1")).thenReturn(List.of(OrderReservation.builder().reservedQty(2).build()));
        ProductSupplyUpdateRequestDTO request = new ProductSupplyUpdateRequestDTO();
        request.setProductId("P-1");
        request.setSupplyType(SupplyType.ABSOLUTE);
        request.setQuantity(1);

        var response = service.updateProductInventory(List.of(request));

        assertTrue(response.get(0).isError());
        assertEquals(4, product.getPhysicalQty());
        assertEquals(3, product.getReservedQty());
        verifyNoInteractions(carts);
        verify(paid, never()).delete(any());
    }
}
