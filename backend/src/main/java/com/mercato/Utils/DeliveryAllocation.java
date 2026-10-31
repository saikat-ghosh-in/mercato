package com.mercato.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Exact paise allocation: a cap and seller coverage must never create or lose money. */
public final class DeliveryAllocation {
    private DeliveryAllocation() {}
    public static Map<String, BigDecimal> split(BigDecimal total, Map<String, BigDecimal> weights) {
        if(total==null || total.signum()<0 || weights==null)throw new IllegalArgumentException("Allocation source and weights must be non-negative");
        try {total=total.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException invalid){throw new IllegalArgumentException("Allocation source must contain whole paise",invalid);}
        for(var entry:weights.entrySet())if(entry.getKey()==null || entry.getValue()==null || entry.getValue().signum()<0)
            throw new IllegalArgumentException("Allocation identities and non-negative weights are required");
        if(weights.isEmpty() && total.signum()>0)throw new IllegalArgumentException("A positive source requires allocation identities");
        Map<String, BigDecimal> result = new TreeMap<>();
        BigDecimal denominator = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // Free merchandise can still carry a fixed buyer charge. Equal weights preserve
        // its source exactly without borrowing a current product price.
        if(denominator.signum()==0) {
            weights=new TreeMap<>(weights);
            weights.replaceAll((key,weight)->BigDecimal.ONE);
            denominator=BigDecimal.valueOf(weights.size());
        }
        BigDecimal cents = total.movePointRight(2);
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
