package com.mercato.commission;
import com.mercato.Service.CommissionService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CommissionGuardrailTest {
    @Test void rejectsOutOfBoundsExcessivePrecisionAndLargeJumps() {
        for (String rate : new String[]{"-0.01", "20.01", "6.001", "10.01", "5"})
            assertThrows(IllegalArgumentException.class, () -> CommissionService.validateChange(new BigDecimal("5"), new BigDecimal(rate), "A detailed reason for this change", "SCHEDULE " + rate + "%"));
    }
    @Test void requiresMeaningfulReasonAndExactConfirmation() {
        assertThrows(IllegalArgumentException.class, () -> CommissionService.validateChange(new BigDecimal("5"), new BigDecimal("6"), "short", "SCHEDULE 6%"));
        assertThrows(IllegalArgumentException.class, () -> CommissionService.validateChange(new BigDecimal("5"), new BigDecimal("6"), "A detailed reason for this change", "YES"));
        assertDoesNotThrow(() -> CommissionService.validateChange(new BigDecimal("5"), new BigDecimal("6.00"), "A detailed reason for this change", "SCHEDULE 6%"));
        assertEquals(60, CommissionService.NOTICE_DAYS);
    }
}
