package com.mercato.order;

import com.mercato.Entity.fulfillment.OrderLine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuantityWorkflowTest {
    @Test void partialCancellationKeepsAcceptanceInsideRemainingQuantity() {
        var line = new OrderLine(); line.setOrderedQty(5); line.accept(5);
        line.cancel(2);
        assertEquals(3, line.getAcceptedQty());
        assertEquals(2, line.getCancelledQty());
    }
    @Test void acceptanceAddsOnlyUnacceptedUnits() {
        var line = new OrderLine(); line.setOrderedQty(5); line.accept(2); line.accept(2);
        assertEquals(4, line.getAcceptedQty());
        assertThrows(IllegalArgumentException.class, () -> line.accept(2));
    }
}
