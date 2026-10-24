package com.mercato.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Commerce.PaymentInbox;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.Jobs.DurableJobs;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CashfreePaymentWebhookTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final DurableJobs jobs=mock(DurableJobs.class);
    private final PaymentInbox inbox=new PaymentInbox(jdbc,new ObjectMapper(),jobs,"test-secret");
    private final byte[] raw="{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{\"order\":{\"order_id\":\"ORD-1\"},\"customer_details\":{\"customer_email\":\"private@example.test\"},\"payment\":{\"cf_payment_id\":\"123\",\"payment_amount\":10.00,\"payment_currency\":\"INR\"}}}\r\n".getBytes(StandardCharsets.UTF_8);
    @Test void authenticationPrecedesParsingAndPersistence() {
        assertThrows(CustomBadRequestException.class,()->inbox.receive(raw,"invalid","1720000000123"));
        verifyNoInteractions(jdbc,jobs);
    }
    @Test void verifiedEventOnlyAcknowledgesAfterInboxAndJobArePersisted() throws Exception {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        inbox.receive(raw,signature(raw),"1720000000123");
        var order=inOrder(jdbc,jobs);
        order.verify(jdbc).update(startsWith("INSERT INTO gateway_event_inbox"),anyString(),eq("ORD-1"),argThat((String encoded)->encoded.contains("10.00")&&!encoded.contains("private@example.test")),any(),eq(raw),eq(signature(raw)),eq("1720000000123"));
        order.verify(jobs).enqueue(eq("PAYMENT_EVENT"),startsWith("gateway-event:"),anyMap());
    }
    @Test void duplicateEventDoesNotEnqueueAnotherJob() throws Exception {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
        inbox.receive(raw,signature(raw),"1720000000123");verifyNoInteractions(jobs);
    }
    @Test void persistenceFailureRemainsRetryableForTheGateway() throws Exception {
        when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        String signature=signature(raw);
        assertThrows(org.springframework.dao.DataAccessException.class,()->inbox.receive(raw,signature,"1720000000123"));verifyNoInteractions(jobs);
    }
    private String signature(byte[] body)throws Exception {
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update("1720000000123".getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
