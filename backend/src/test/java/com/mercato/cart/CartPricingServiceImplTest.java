package com.mercato.cart;

import com.mercato.Entity.Product;
import com.mercato.Entity.cart.Cart;
import com.mercato.Entity.cart.CartItem;
import com.mercato.Entity.cart.ChargeType;
import com.mercato.Service.CartPricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartPricingServiceImplTest {

    private final CartPricingServiceImpl pricingService = new CartPricingServiceImpl(new com.mercato.Service.PackageDeliveryPricing());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pricingService, "CART_SHIPPING_THRESHOLD", BigDecimal.valueOf(500));
        ReflectionTestUtils.setField(pricingService, "CART_SHIPPING_CHARGE", BigDecimal.valueOf(50));
        ReflectionTestUtils.setField(pricingService, "CART_PLATFORM_FEE", BigDecimal.valueOf(12));
    }

    @Test
    void applyCharges_addsShippingWhenBelowThreshold() {
        Cart cart = cartWithSubtotal(BigDecimal.valueOf(400));

        pricingService.applyCharges(cart);

        assertEquals(BigDecimal.valueOf(50), cart.getChargeAmount(ChargeType.SHIPPING));
    }

    @Test
    void applyCharges_skipsShippingWhenAboveThreshold() {
        Cart cart = cartWithSubtotal(BigDecimal.valueOf(600));

        pricingService.applyCharges(cart);

        assertEquals(BigDecimal.ZERO, cart.getChargeAmount(ChargeType.SHIPPING));
    }

    @Test
    void applyCharges_addsPlatformFee() {
        Cart cart = cartWithSubtotal(BigDecimal.valueOf(100));

        pricingService.applyCharges(cart);

        assertEquals(BigDecimal.valueOf(12), cart.getChargeAmount(ChargeType.PLATFORM_FEE));
        assertTrue(cart.findCharge(ChargeType.PLATFORM_FEE).get().getDescription().contains("Platform Fee"));
    }

    @Test
    void applyCharges_clearsChargesWhenSubtotalIsZero() {
        Cart cart = new Cart();
        cart.addOrUpdateCharge(ChargeType.SHIPPING, BigDecimal.TEN, "Standard Shipping");
        cart.addOrUpdateCharge(ChargeType.PLATFORM_FEE, BigDecimal.TEN, "Platform Fee");

        pricingService.applyCharges(cart);

        assertTrue(cart.getCharges().isEmpty());
    }

    private Cart cartWithSubtotal(BigDecimal unitPrice) {
        Product product = Product.builder()
                .productId("P-1")
                .sellingPrice(unitPrice)
                .build();

        CartItem item = CartItem.builder()
                .product(product)
                .quantity(1)
                .build();

        Cart cart = new Cart();
        cart.setCartItems(List.of(item));
        return cart;
    }

    @Test
    void deliveryChoiceRepricesAtThresholdAndDoesNotAccumulateSurcharges() {
        Cart cart = cartWithSubtotal(BigDecimal.valueOf(500));
        pricingService.applyCharges(cart);
        assertEquals(BigDecimal.ZERO, cart.getChargeAmount(ChargeType.SHIPPING));
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);
        pricingService.applyCharges(cart);
        assertEquals(BigDecimal.valueOf(99), cart.getChargeAmount(ChargeType.SHIPPING));
        pricingService.applyCharges(cart);
        assertEquals(BigDecimal.valueOf(99), cart.getChargeAmount(ChargeType.SHIPPING));
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.CONSOLIDATED);
        pricingService.applyCharges(cart);
        assertEquals(BigDecimal.ZERO, cart.getChargeAmount(ChargeType.SHIPPING));
        assertEquals(3, cart.getDeliveryOptions().size());
        Cart small = cartWithSubtotal(BigDecimal.valueOf(499));
        small.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);
        pricingService.applyCharges(small);
        assertEquals(BigDecimal.valueOf(149), small.getChargeAmount(ChargeType.SHIPPING));
    }

    @Test void bulkyFeesMultiplyByUnitsSurviveFreeBaseDeliveryAndDoNotAccumulate() {
        Cart cart = cartWithSubtotal(new BigDecimal("1000"));
        var item = cart.getCartItems().get(0); item.setQuantity(2);
        var product = item.getProduct(); product.setWeightKg(new BigDecimal("6"));
        product.setLengthCm(new BigDecimal("20")); product.setWidthCm(new BigDecimal("20")); product.setHeightCm(new BigDecimal("20"));
        pricingService.applyCharges(cart);
        assertEquals(new BigDecimal("100.00"), cart.getChargeAmount(ChargeType.SHIPPING));
        assertEquals(new BigDecimal("100.00"), cart.getDeliveryOptions().get(0).bulkyItemSurcharge());
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);
        pricingService.applyCharges(cart); pricingService.applyCharges(cart);
        assertEquals(new BigDecimal("199.00"), cart.getChargeAmount(ChargeType.SHIPPING));
        item.setQuantity(1); pricingService.applyCharges(cart);
        assertEquals(new BigDecimal("149.00"), cart.getChargeAmount(ChargeType.SHIPPING));
    }
}
