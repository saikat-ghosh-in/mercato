package com.mercato.cart;
import com.mercato.Utils.DeliveryAllocation;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryAllocationTest {
    @Test void capIsAllocatedToExactPaiseAndIndependentOfInsertionOrder() {
        var weights = new LinkedHashMap<String,BigDecimal>();
        weights.put("C",BigDecimal.ONE); weights.put("B",BigDecimal.ONE); weights.put("A",BigDecimal.ONE);
        var split = DeliveryAllocation.split(new BigDecimal("250"), weights);
        assertEquals(new BigDecimal("83.34"),split.get("A"));
        assertEquals(new BigDecimal("83.33"),split.get("B"));
        assertEquals(new BigDecimal("250.00"),split.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(split,DeliveryAllocation.split(new BigDecimal("250"),new TreeMap<>(weights)));
    }
    @Test void zeroWeightLinesReceiveNoFeeAndEmptyCartsAreSafe() {
        var split=DeliveryAllocation.split(new BigDecimal("50"),Map.of("free",BigDecimal.ZERO,"paid",BigDecimal.TEN));
        assertEquals(new BigDecimal("0.00"),split.get("free")); assertEquals(new BigDecimal("50.00"),split.get("paid"));
        assertTrue(DeliveryAllocation.split(BigDecimal.ZERO,Map.of()).isEmpty());
    }
    @Test void allZeroWeightsStillConserveEveryPaiseWithStableTies() {
        var shares=DeliveryAllocation.split(new BigDecimal("0.05"),Map.of("C",BigDecimal.ZERO,"B",BigDecimal.ZERO,"A",BigDecimal.ZERO));
        assertEquals(Map.of("A",new BigDecimal("0.02"),"B",new BigDecimal("0.02"),"C",new BigDecimal("0.01")),shares);
    }
    @Test void invalidSourcesCannotCreateNegativeSharesOrLoseFractionalPaise() {
        assertThrows(IllegalArgumentException.class,()->DeliveryAllocation.split(new BigDecimal("-0.01"),Map.of("A",BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class,()->DeliveryAllocation.split(new BigDecimal("0.001"),Map.of("A",BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class,()->DeliveryAllocation.split(BigDecimal.ONE,Map.of("A",new BigDecimal("-1"),"B",new BigDecimal("2"))));
        assertThrows(IllegalArgumentException.class,()->DeliveryAllocation.split(BigDecimal.ONE,Map.of()));
    }
    @Test void unevenAndTinyAllocationsConserveSourceIndependentlyOfInputOrder() {
        var random=new Random(4051);
        for(int sample=0;sample<300;sample++) {
            var weights=new LinkedHashMap<String,BigDecimal>();
            int size=1+random.nextInt(30);
            for(int i=size;i>0;i--)weights.put(String.format("unit-%02d",i),BigDecimal.valueOf(random.nextInt(15)));
            var source=BigDecimal.valueOf(random.nextInt(10000),2);
            var shares=DeliveryAllocation.split(source,weights);
            assertEquals(source,shares.values().stream().reduce(new BigDecimal("0.00"),BigDecimal::add));
            assertEquals(shares,DeliveryAllocation.split(source,new TreeMap<>(weights)));
            assertTrue(shares.values().stream().allMatch(v->v.scale()==2 && v.signum()>=0 && v.compareTo(source)<=0));
        }
    }
}
