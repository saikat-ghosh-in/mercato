package com.mercato.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CommissionMath {
    public static final BigDecimal CAP_PER_ITEM = new BigDecimal("500.00");
    private CommissionMath() {}

    public static BigDecimal forUnits(BigDecimal unitPrice, int quantity, BigDecimal rate, BigDecimal capPerItem) {
        if (rate == null || quantity <= 0) return BigDecimal.ZERO.setScale(2);
        BigDecimal rawUnitFee = unitPrice.multiply(rate).divide(new BigDecimal("100"));
        // Legacy orders preserve aggregate rounding and their uncapped captured terms.
        if (capPerItem == null) return rawUnitFee.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        return rawUnitFee.setScale(2, RoundingMode.HALF_UP).min(capPerItem).multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
