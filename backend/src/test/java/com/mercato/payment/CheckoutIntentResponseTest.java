package com.mercato.payment;
import com.mercato.Controller.OrderController;
import com.mercato.Commerce.*;
import com.mercato.Service.*;
import com.mercato.Utils.AuthUtil;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CheckoutIntentResponseTest {
    @Test void privateRecoveryResponseCannotBeCached(){var recovery=mock(PaymentSessionRecovery.class);when(recovery.recoverIntent("opaque")).thenReturn(Map.of("state","REPLAY_CAPTURE"));var controller=new OrderController(mock(OrderService.class),mock(FulfillmentService.class),mock(CashfreeService.class),mock(OrderLineUpdateService.class),mock(AuthUtil.class),mock(PaymentInbox.class),recovery);var response=controller.checkoutIntent("opaque");assertEquals("private, no-store",response.getHeaders().getCacheControl());assertEquals(Map.of("state","REPLAY_CAPTURE"),response.getBody());verify(recovery).recoverIntent("opaque");}
}
