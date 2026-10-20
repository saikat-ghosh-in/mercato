package com.mercato.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Exact paise allocation: a cap and seller coverage must never create or lose money. */
public final class DeliveryAllocation {
    private DeliveryAllocation() {}
    public static Map<String, BigDecimal> split(BigDecimal total, Map<String, BigDecimal> weights) {
        Map<String, BigDecimal> result = new TreeMap<>();
        BigDecimal denominator = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (denominator.signum() == 0) { weights.keySet().forEach(key -> result.put(key, BigDecimal.ZERO.setScale(2))); return result; }
        BigDecimal cents = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        record Fraction(String key, BigDecimal remainder) {}
        List<Fraction> fractions = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (var entry : new TreeMap<>(weights).entrySet()) {
            BigDecimal numerator = cents.multiply(entry.getValue());
            BigDecimal floor = numerator.divide(denominator, 0, RoundingMode.DOWN);
            result.put(entry.getKey(), floor.movePointLeft(2).setScale(2)); assigned = assigned.add(floor);
            fractions.add(new Fraction(entry.getKey(), numerator.subtract(floor.multiply(denominator))));
        }
        fractions.sort(Comparator.comparing(Fraction::remainder).reversed().thenComparing(Fraction::key));
        for (int i = 0, extra = cents.subtract(assigned).intValueExact(); i < extra; i++) {
            String key = fractions.get(i).key(); result.put(key, result.get(key).add(new BigDecimal("0.01")));
        }
        return result;
    }
}
