package com.mercato.order;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Product;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Repository.CartRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Service.CartPricingService;
import com.mercato.Service.CartReservationService;
import com.mercato.Service.CartService;
import com.mercato.Service.OrderRetryHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRetryHoldServiceTest {

    private static final String PRODUCT_ID = "P-100";

    @Mock private CartService cartService;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartReservationService cartReservationService;
    @Mock private CartPricingService cartPricingService;

    @InjectMocks
    private OrderRetryHoldService orderRetryHoldService;

    private EcommUser user;
    private Order order;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new EcommUser();
        user.setUserId("USR-1");

        product = Product.builder()
                .productId(PRODUCT_ID)
                .physicalQty(10)
                .reservedQty(0)
                .build();

        cart = new Cart();
        cart.setCartItems(new ArrayList<>());

        OrderLine orderLine = new OrderLine();
        orderLine.setProductId(PRODUCT_ID);
        orderLine.setOrderedQty(2);

        order = new Order();
        order.addOrderLine(orderLine);

        when(cartService.getCartForOrderAllocation(user,order)).thenReturn(cart);
        when(productRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        org.mockito.Mockito.lenient().when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(inv->{
            Cart saved=inv.getArgument(0);saved.getCartItems().forEach(item->item.setCartItemId(100L));return saved;
        });
    }

    @Test
    void ensureCartHoldsForRetry_restoresMissingCartItemAndReserves() {
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        orderRetryHoldService.ensureCartHoldsForRetry(order, user);

        CartItem cartItem = cart.findItemByProductId(PRODUCT_ID).orElseThrow();
        verify(cartReservationService).reserve(cartItem);
        verify(cartPricingService).applyCharges(cart);
        verify(cartRepository).save(cart);
        org.junit.jupiter.api.Assertions.assertEquals(1L,cart.getVersion(),"Recreating bag selections invalidates its previous quote");
        org.junit.jupiter.api.Assertions.assertEquals(cartItem.getCartItemId(),order.getOrderLines().get(0).getRetryCartItemId());
        org.junit.jupiter.api.Assertions.assertEquals(cartItem.getSelectionGeneration(),order.getOrderLines().get(0).getRetrySelectionGeneration());
    }
    @Test void restoringReducedRetryQuantitiesInvalidatesTheCartQuote() {
        cart.addProduct(product,1);
        orderRetryHoldService.ensureCartHoldsForRetry(order,user);
        org.junit.jupiter.api.Assertions.assertEquals(2,cart.getCartItems().get(0).getQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(1L,cart.getVersion());
        org.junit.jupiter.api.Assertions.assertEquals(cart.getCartItems().get(0).getSelectionGeneration(),order.getOrderLines().get(0).getRetrySelectionGeneration());
    }
    @Test void adequateNewBuyerSelectionIsNotAdoptedByTheRetry() {
        cart.addProduct(product,2);cart.getCartItems().get(0).setCartItemId(101L);
        orderRetryHoldService.ensureCartHoldsForRetry(order,user);
        org.junit.jupiter.api.Assertions.assertNull(order.getOrderLines().get(0).getRetryCartItemId());
        org.junit.jupiter.api.Assertions.assertNull(order.getOrderLines().get(0).getRetrySelectionGeneration());
        org.mockito.Mockito.verify(cartRepository,org.mockito.Mockito.never()).saveAndFlush(any());
    }
    @Test void retryCannotPayForAnUnapprovedProductAlreadyInTheBag() {
        cart.addProduct(product, 2);
        product.setListingStatus("PENDING");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> orderRetryHoldService.ensureCartHoldsForRetry(order,user));
        org.mockito.Mockito.verifyNoInteractions(cartReservationService,cartPricingService,cartRepository);
    }
    @Test void retryCannotRestoreADisabledListingToTheBag() {
        product.setActive(false);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> orderRetryHoldService.ensureCartHoldsForRetry(order,user));
        org.mockito.Mockito.verifyNoInteractions(cartReservationService,cartPricingService,cartRepository);
    }
}
