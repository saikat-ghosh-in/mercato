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
}
