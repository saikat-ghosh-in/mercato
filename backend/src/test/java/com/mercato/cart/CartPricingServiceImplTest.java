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

        assertShipping("50", cart);
    }

    @Test
    void applyCharges_skipsShippingWhenAboveThreshold() {
        Cart cart = cartWithSubtotal(BigDecimal.valueOf(600));

        pricingService.applyCharges(cart);

        assertShipping("0", cart);
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

    @Test void largeSavedBagKeepsItsExactHandlingQuoteBeforeCheckoutLimitValidation() {
        Cart cart=cartWithSubtotal(BigDecimal.ONE);
        cart.setCartItems(new java.util.ArrayList<>(cart.getCartItems()));
        cart.getCartItems().get(0).setQuantity(Integer.MAX_VALUE);
        cart.addCartItem(CartItem.builder().product(Product.builder().productId("P-2").sellingPrice(BigDecimal.ONE).build())
                .quantity(Integer.MAX_VALUE).build());
        pricingService.applyCharges(cart);
        assertEquals(0,new BigDecimal("8589934595").compareTo(cart.getChargeAmount(ChargeType.PROCESSING_AND_HANDLING)));
    }

    private void assertShipping(String expected, Cart cart) { assertEquals(0, new BigDecimal(expected).compareTo(cart.getChargeAmount(ChargeType.SHIPPING))); }

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
        assertShipping("0", cart);
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);
        pricingService.applyCharges(cart);
        assertShipping("49", cart);
        pricingService.applyCharges(cart);
        assertShipping("49", cart);
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.CONSOLIDATED);
        pricingService.applyCharges(cart);
        assertShipping("0", cart);
        assertEquals(3, cart.getDeliveryOptions().size());
        Cart small = cartWithSubtotal(BigDecimal.valueOf(499));
        small.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);
        pricingService.applyCharges(small);
        assertShipping("99", small);
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
        assertEquals(new BigDecimal("149.00"), cart.getChargeAmount(ChargeType.SHIPPING));
        item.setQuantity(1); pricingService.applyCharges(cart);
        assertEquals(new BigDecimal("99.00"), cart.getChargeAmount(ChargeType.SHIPPING));
    }

    private void makeBulky(Product product) {
        product.setWeightKg(new BigDecimal("12")); product.setLengthCm(new BigDecimal("110"));
        product.setWidthCm(new BigDecimal("60")); product.setHeightCm(new BigDecimal("45"));
    }
    @Test void fullBagCapAppliesBeforeSellerCoverageAndExpeditedUpgrade() {
        Cart cart=cartWithSubtotal(new BigDecimal("1000"));var item=cart.getCartItems().get(0);makeBulky(item.getProduct());item.setQuantity(2);
        pricingService.applyCharges(cart);assertShipping("250",cart);
        assertEquals(0,new BigDecimal("250").compareTo(cart.getDeliveryOptions().get(0).bulkyItemSurcharge()));
        item.getProduct().setSellerPaysShipping(true);pricingService.applyCharges(cart);assertShipping("0",cart);
        assertEquals(new BigDecimal("250.00"),item.getSellerDeliveryCharge());assertTrue(cart.getDeliveryOptions().get(0).sellerPaidStandard());
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);pricingService.applyCharges(cart);assertShipping("49",cart);
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.CONSOLIDATED);pricingService.applyCharges(cart);assertShipping("0",cart);
        assertTrue(cart.getDeliveryOptions().get(2).description().contains("carbon emissions"));
        item.getProduct().setSellerPaysShipping(false);pricingService.applyCharges(cart);assertShipping("250",cart);
        assertEquals(0,item.getSellerDeliveryCharge().signum());
    }
    @Test void mixedCoverageNeverWaivesAnotherItemsCost() {
        Cart cart=cartWithSubtotal(new BigDecimal("1000"));var first=cart.getCartItems().get(0);makeBulky(first.getProduct());first.getProduct().setSellerPaysShipping(true);
        var second=CartItem.builder().quantity(1).product(Product.builder().productId("P-2").sellingPrice(new BigDecimal("1000")).build()).build();makeBulky(second.getProduct());
        cart.setCartItems(List.of(first,second));pricingService.applyCharges(cart);assertShipping("125",cart);
        assertEquals(0,first.getBuyerNormalDeliveryCharge().signum());
        assertEquals(new BigDecimal("125.00"),second.getBuyerNormalDeliveryCharge());
        assertEquals(new BigDecimal("125.00"),first.getSellerDeliveryCharge());assertEquals(0,second.getSellerDeliveryCharge().signum());
        cart.setDeliveryMethod(com.mercato.Entity.cart.DeliveryMethod.EXPEDITED);pricingService.applyCharges(cart);assertShipping("174",cart);
        assertEquals(new BigDecimal("24.50"),first.getBuyerExpeditedDeliveryCharge());
        assertEquals(new BigDecimal("24.50"),second.getBuyerExpeditedDeliveryCharge());
    }
    @Test void thresholdBaseChargeIsSplitByItemValueWithExactTotals() {
        Cart cart=cartWithSubtotal(new BigDecimal("100"));var first=cart.getCartItems().get(0);first.getProduct().setSellerPaysShipping(true);
        var second=CartItem.builder().quantity(1).product(Product.builder().productId("P-2").sellingPrice(new BigDecimal("200")).build()).build();
        cart.setCartItems(List.of(first,second));pricingService.applyCharges(cart);assertShipping("33.33",cart);
        assertEquals(new BigDecimal("16.67"),first.getSellerDeliveryCharge());
        assertEquals(new BigDecimal("50.00"),first.getSellerDeliveryCharge().add(cart.getChargeAmount(ChargeType.SHIPPING)));
    }
}
