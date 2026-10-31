package com.mercato.cart;

import com.mercato.Entity.Product;
import com.mercato.Entity.cart.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CartSelectionProvenanceTest {
    @Test void selectedDiscountAndPriceSurviveProductChangesAndQuantityIncreases() {
        var product=Product.builder().productId("P-PRICE").retailPrice(new BigDecimal("100.00"))
            .discountPercent(new BigDecimal("12.50")).sellingPrice(new BigDecimal("87.50")).build();
        var cart=new Cart();cart.addProduct(product,2);var item=cart.getCartItems().get(0);
        product.setRetailPrice(new BigDecimal("200.00"));product.setDiscountPercent(BigDecimal.ZERO);product.setSellingPrice(new BigDecimal("200.00"));
        cart.addProduct(product,1);
        assertEquals(new BigDecimal("87.50"),item.getItemPrice());assertEquals(3,item.getQuantity());
        assertEquals(new BigDecimal("100.00"),item.getSelectedRetailPrice());
        assertEquals(new BigDecimal("12.50"),item.getSelectedDiscountPercent());
        assertEquals("PRODUCT_PRICE_AT_SELECTION",item.getSelectedPriceProvenance());
    }
    @Test void oldSelectedPriceCannotBorrowTodaysProductDiscount() {
        var product=Product.builder().productId("P-OLD").retailPrice(new BigDecimal("100.00"))
            .discountPercent(new BigDecimal("20.00")).sellingPrice(new BigDecimal("80.00")).build();
        var item=CartItem.builder().product(product).quantity(2).unitPrice(new BigDecimal("75.00")).build();
        var cart=new Cart();cart.addCartItem(item);cart.addProduct(product,1);
        assertEquals(new BigDecimal("75.00"),item.getItemPrice());assertNull(item.getSelectedRetailPrice());
        assertNull(item.getSelectedDiscountPercent());assertNull(item.getSelectedPriceProvenance());
    }
    @Test void aReducedSelectionHasANewGenerationButAdditionalQuantityDoesNot() {
        var item=CartItem.builder().quantity(3).unitPrice(BigDecimal.ONE).build();var generation=item.getSelectionGeneration();
        item.addQuantity(2);assertEquals(generation,item.getSelectionGeneration());
        item.updateQuantity(1);assertNotEquals(generation,item.getSelectionGeneration());
    }
    @Test void largeCartTotalsCannotWrapAndQuantityAdditionCannotOverflow() {
        var cart=new Cart();cart.setCartItems(java.util.List.of(CartItem.builder().quantity(Integer.MAX_VALUE).build(),CartItem.builder().quantity(Integer.MAX_VALUE).build()));
        assertEquals(4294967294L,cart.getCartQuantity());
        assertThrows(ArithmeticException.class,()->cart.getCartItems().get(0).addQuantity(1));
    }
    @Test void reducingOrReplacingARetryBoundSelectionDoesNotAuthorizeItsNewGeneration() {
        var item=CartItem.builder().cartItemId(100L).quantity(2).unitPrice(BigDecimal.ONE).build();
        var line=new com.mercato.Entity.fulfillment.OrderLine();line.setRetryCartItemId(item.getCartItemId());line.setRetrySelectionGeneration(item.getSelectionGeneration());
        assertTrue(com.mercato.Service.CartServiceImpl.sameSelection(item,line));
        item.updateQuantity(1);assertFalse(com.mercato.Service.CartServiceImpl.sameSelection(item,line));
        item.addQuantity(1);assertFalse(com.mercato.Service.CartServiceImpl.sameSelection(item,line));
        var replacement=CartItem.builder().cartItemId(101L).quantity(2).unitPrice(BigDecimal.ONE).build();
        assertFalse(com.mercato.Service.CartServiceImpl.sameSelection(replacement,line));
    }
}
