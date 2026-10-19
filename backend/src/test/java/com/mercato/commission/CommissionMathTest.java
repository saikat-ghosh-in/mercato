package com.mercato.commission;

import com.mercato.Utils.CommissionMath;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CommissionMathTest {
    BigDecimal fee(String price, int qty, String rate, BigDecimal cap) { return CommissionMath.forUnits(new BigDecimal(price),qty,new BigDecimal(rate),cap); }
    @Test void currentRateIsHalfAPercentWith500CapPerSoldItem() {
        assertEquals(new BigDecimal("5.00"),fee("1000",1,"0.5",CommissionMath.CAP_PER_ITEM));
        assertEquals(new BigDecimal("500.00"),fee("100000",1,"0.5",CommissionMath.CAP_PER_ITEM));
        assertEquals(new BigDecimal("1000.00"),fee("200000",2,"0.5",CommissionMath.CAP_PER_ITEM));
    }
    @Test void fulfilmentQuantitiesAndLegacyCapturedTermsRemainIndependent() {
        assertEquals(new BigDecimal("500.00"),fee("200000",1,"0.5",CommissionMath.CAP_PER_ITEM));
        assertEquals(new BigDecimal("0.00"),fee("200000",0,"0.5",CommissionMath.CAP_PER_ITEM));
        assertEquals(new BigDecimal("10000.00"),fee("200000",1,"5",null));
        assertEquals(new BigDecimal("0.01"),fee("0.99",2,"0.5",null));
        assertEquals(new BigDecimal("0.00"),fee("0.99",2,"0.5",CommissionMath.CAP_PER_ITEM));
    }
}
