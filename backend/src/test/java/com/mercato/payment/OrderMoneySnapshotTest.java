package com.mercato.payment;

import com.mercato.Commerce.OrderMoneySnapshot;
import com.mercato.Entity.Product;
import com.mercato.Entity.cart.*;
import com.mercato.Entity.fulfillment.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderMoneySnapshotTest {
    static BigDecimal money(String value){return new BigDecimal(value);}
    Order order(){var o=new Order();o.setOrderId("ORD-UNIT-TEST");o.setSubtotal(money("2.02"));o.setShippingCharge(money("0.05"));o.setPlatformFee(money("0.03"));o.setHandlingCharge(money("0.01"));o.setCharges(money("0.09"));o.setTotalAmount(money("2.11"));return o;}
    CartItem item(String id,int qty,String net,String retail,String discount) {
        var p=Product.builder().productId(id).sellingPrice(money(net)).retailPrice(money(retail)).discountPercent(money(discount)).build();
        var cart=new Cart();cart.addProduct(p,qty);return cart.getCartItems().get(0);
    }
    OrderLine line(Order o,CartItem item,int number){var l=new OrderLine();l.setOrderLineNumber(number);l.setProductId(item.getProduct().getProductId());l.setUnitPrice(item.getItemPrice());l.setOrderedQty(item.getQuantity());l.setLineTotal(item.getLineTotal());l.setCommissionRatePercent(money("0.50"));l.setCommissionCapPerItem(money("500.00"));o.addOrderLine(l);return l;}
    @Test void quotedComponentsAndSelectedDiscountsAreImmutableExactUnitSources() {
        var o=order();var first=item("A",2,"1.01","2.02","50.00");
        first.setSellerDeliveryCharge(money("0.03"));first.setBuyerNormalDeliveryCharge(money("0.00"));first.setBuyerExpeditedDeliveryCharge(money("0.05"));
        var l=line(o,first,1);OrderMoneySnapshot.snapshot(o,List.of(first));
        assertEquals(2,l.getMoneyUnits().size());var a=l.getMoneyUnits().get(0);var b=l.getMoneyUnits().get(1);
        assertEquals(101,a.getNetMerchandisePaise());assertEquals(202L,a.getRetailPaise());assertEquals(101L,a.getDiscountPaise());
        assertEquals(0,a.getBuyerNormalDeliveryPaise());assertEquals(3,a.getBuyerExpeditedDeliveryPaise());assertEquals(2,b.getBuyerExpeditedDeliveryPaise());
        assertEquals(2,a.getPlatformPaise());assertEquals(1,b.getPlatformPaise());assertEquals(1,a.getHandlingPaise());assertEquals(0,b.getHandlingPaise());
        assertEquals(2,a.getSellerDeliveryPaise());assertEquals(1,b.getSellerDeliveryPaise());
        assertEquals(211,a.buyerTotalPaise()+b.buyerTotalPaise());assertEquals(1L,a.getCommissionPaise());assertEquals(1,o.getFinancialPolicyVersion());
        var ids=l.getMoneyUnits().stream().map(OrderMoneyUnit::getUnitId).toList();l.cancel(1);
        assertEquals(ids,l.getMoneyUnits().stream().map(OrderMoneyUnit::getUnitId).toList());
        assertThrows(IllegalStateException.class,()->OrderMoneySnapshot.snapshot(o,List.of(first)),"A snapshot cannot be repriced after creation");
    }
    @Test void unknownOldDiscountRemainsUnknownAndSellerCoverageNeverBecomesBuyerNormalShipping() {
        var o=order();o.setSubtotal(money("2.02"));
        var first=item("A",1,"1.01","2.02","50.00");var second=item("B",1,"1.01","2.02","50.00");
        first.setSelectedRetailPrice(null);first.setSelectedDiscountPercent(null);first.setSelectedPriceProvenance(null);
        first.setSellerDeliveryCharge(money("0.07"));first.setBuyerExpeditedDeliveryCharge(money("0.02"));
        second.setBuyerNormalDeliveryCharge(money("0.01"));second.setBuyerExpeditedDeliveryCharge(money("0.02"));
        line(o,first,1);line(o,second,2);OrderMoneySnapshot.snapshot(o,List.of(first,second));
        assertEquals(1,o.getOrderLines().get(0).getMoneyUnits().size());assertEquals(1,o.getOrderLines().get(1).getMoneyUnits().size());
        var a=o.getOrderLines().get(0).getMoneyUnits().get(0);var b=o.getOrderLines().get(1).getMoneyUnits().get(0);
        assertNull(a.getRetailPaise());assertNull(a.getDiscountPaise());assertEquals("UNKNOWN",a.getPriceProvenance());
        assertEquals(0,a.getBuyerNormalDeliveryPaise());assertEquals(7,a.getSellerDeliveryPaise());assertEquals(1,b.getBuyerNormalDeliveryPaise());
        assertEquals(211,a.buyerTotalPaise()+b.buyerTotalPaise());
    }
    @Test void unitCommissionCapUsesCapturedTermsAndNoAggregateRoundingForNewCappedUnits() {
        var o=order();var item=item("A",2,"100000.01","100000.01","0");var l=line(o,item,1);
        o.setSubtotal(money("200000.02"));o.setShippingCharge(BigDecimal.ZERO);o.setPlatformFee(BigDecimal.ZERO);o.setHandlingCharge(BigDecimal.ZERO);o.setCharges(BigDecimal.ZERO);o.setTotalAmount(o.getSubtotal());
        OrderMoneySnapshot.snapshot(o,List.of(item));assertEquals(2,l.getMoneyUnits().size());assertEquals(50000L,l.getMoneyUnits().get(0).getCommissionPaise());assertEquals(50000L,l.getMoneyUnits().get(1).getCommissionPaise());
    }
    @Test void excessiveQuantityFailsBeforeAnyUnitMaterialization() {
        var o=order();var item=item("A",Integer.MAX_VALUE,"1.00","1.00","0");line(o,item,1);
        assertThrows(IllegalArgumentException.class,()->OrderMoneySnapshot.snapshot(o,List.of(item)));
        assertTrue(o.getOrderLines().get(0).getMoneyUnits().isEmpty());
    }
    @Test void checkoutLimitAllowsExactlyTenThousandAndRejectsTheNextUnitBeforeMaterializing() {
        var o=order();var item=item("A",10000,"0.00","0.00","0");var l=line(o,item,1);
        o.setSubtotal(money("0.00"));o.setShippingCharge(money("0.00"));o.setPlatformFee(money("0.01"));o.setHandlingCharge(money("0.00"));o.setCharges(money("0.01"));o.setTotalAmount(money("0.01"));
        OrderMoneySnapshot.snapshot(o,List.of(item));assertEquals(10000,l.getMoneyUnits().size());
        assertEquals(1,l.getMoneyUnits().stream().mapToLong(OrderMoneyUnit::buyerTotalPaise).sum());
        var excessive=order();var tooMany=item("A",10001,"0.00","0.00","0");var excessLine=line(excessive,tooMany,1);
        assertThrows(IllegalArgumentException.class,()->OrderMoneySnapshot.snapshot(excessive,List.of(tooMany)));assertTrue(excessLine.getMoneyUnits().isEmpty());
    }

    @Test void attributionUsesExplicitBucketsThenStableUnitNumbersWithoutGuessingPhysicalHistory() {
        var o=order();var item=item("A",2,"1.01","2.02","50.00");item.setBuyerExpeditedDeliveryCharge(money("0.05"));
        var line=line(o,item,1);OrderMoneySnapshot.snapshot(o,List.of(item));
        var first=line.getMoneyUnits().get(0);var second=line.getMoneyUnits().get(1);
        assertEquals(List.of(second,first),OrderMoneySnapshot.selectUnits(line,List.of(List.of(second.getUnitId()),List.of(first.getUnitId())),2));
        assertEquals(List.of(first,second),OrderMoneySnapshot.selectUnits(line,List.of(List.of(second.getUnitId(),first.getUnitId())),2));
    }
    @Test void attributionRejectsUnknownDuplicateAndInsufficientIdentitiesAndLegacyLines() {
        var o=order();var item=item("A",2,"1.01","2.02","50.00");item.setBuyerExpeditedDeliveryCharge(money("0.05"));
        var line=line(o,item,1);OrderMoneySnapshot.snapshot(o,List.of(item));String id=line.getMoneyUnits().get(0).getUnitId();
        assertThrows(IllegalArgumentException.class,()->OrderMoneySnapshot.selectUnits(line,List.of(List.of("foreign-unit")),1));
        assertThrows(IllegalArgumentException.class,()->OrderMoneySnapshot.selectUnits(line,List.of(List.of(id),List.of(id)),1));
        assertThrows(IllegalArgumentException.class,()->OrderMoneySnapshot.selectUnits(line,List.of(List.of(id)),2));
        assertThrows(IllegalArgumentException.class,()->OrderMoneySnapshot.selectUnits(new OrderLine(),List.of(),1));
    }
}
