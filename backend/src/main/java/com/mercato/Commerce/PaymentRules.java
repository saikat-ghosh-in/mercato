package com.mercato.Commerce;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaymentRules {
    private PaymentRules() {}
    public static BigDecimal decimal(String value) {
        try {
            BigDecimal result = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
            if (result.signum()<0 || result.precision()>15) throw new IllegalArgumentException("Invalid money amount");
            return result;
        } catch (ArithmeticException | NumberFormatException e) { throw new IllegalArgumentException("Invalid money amount",e); }
    }
    public static BigDecimal refundable(BigDecimal captured, BigDecimal returned, BigDecimal inProgress) {
        if(captured==null || returned==null || inProgress==null || captured.signum()<0 || returned.signum()<0 || inProgress.signum()<0)
            throw new IllegalArgumentException("Verified capture and refund amounts are required");
        BigDecimal remaining=captured.subtract(returned).subtract(inProgress);
        if(remaining.signum()<0) throw new IllegalArgumentException("Refund obligations exceed capture");
        return remaining.setScale(2,RoundingMode.UNNECESSARY);
    }
}
