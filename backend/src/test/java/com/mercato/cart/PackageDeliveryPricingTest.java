package com.mercato.cart;

import com.mercato.Entity.Product;
import com.mercato.Service.PackageDeliveryPricing;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PackageDeliveryPricingTest {
    final PackageDeliveryPricing pricing = new PackageDeliveryPricing();
    Product pack(String kg, String l, String w, String h) {
        return Product.builder().weightKg(new BigDecimal(kg)).lengthCm(new BigDecimal(l)).widthCm(new BigDecimal(w)).heightCm(new BigDecimal(h)).build();
    }
    void fee(String amount, Product p) { assertEquals(0, new BigDecimal(amount).compareTo(pricing.surcharge(p))); }
    @Test void exactThresholdsAreIncludedAndEachStartedWeightBlockIsCharged() {
        fee("0",pack("5","100","5","5")); fee("50",pack("5.001","20","20","20"));
        fee("50",pack("10","20","20","20")); fee("100",pack("10.001","20","20","20"));
    }
    @Test void volumetricWeightAndOversizeMinimumUseLargerChargeWithoutDoubleCharging() {
        fee("0",pack("1","50","25","20")); // exactly 5 volumetric kg
        fee("50",pack("1","50","25","20.01"));
        fee("100",pack("1","100.01","5","5"));
        fee("150",pack("20","110","5","5"));
        fee("550",pack("12","110","60","45"));
    }
    @Test void unknownLegacyPackagesKeepExistingPrice() { fee("0",new Product()); }
}
