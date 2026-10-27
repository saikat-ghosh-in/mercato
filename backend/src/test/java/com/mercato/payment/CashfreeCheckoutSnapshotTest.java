package com.mercato.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Commerce.*;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.fulfillment.Order;
import com.mercato.Service.CashfreeServiceImpl;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CashfreeCheckoutSnapshotTest {
    @Test void gatewayRetriesUseTheOriginalImmutableCustomerBody() {
        var gateway=mock(CashfreeGateway.class);var json=new ObjectMapper();var bodies=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        when(gateway.post(eq("/orders"),any(),eq("create-order:ORD-SNAPSHOT"))).thenAnswer(call->{bodies.add(json.valueToTree(call.getArgument(1)));return json.createObjectNode().put("cf_order_id","cf-snapshot").put("payment_session_id","same-session");});
        var service=new CashfreeServiceImpl(gateway,mock(PaymentInbox.class),mock(com.mercato.Repository.OrderRepository.class),mock(com.mercato.Utils.AuthUtil.class),mock(com.mercato.Jobs.DurableJobs.class),mock(org.springframework.transaction.PlatformTransactionManager.class),"http://localhost/return","http://localhost/notify");
        var order=new Order();order.setOrderId("ORD-SNAPSHOT");order.setCustomerUserId("buyer-original");order.setCustomerName("Original buyer");order.setCustomerEmail("original@test.invalid");order.setRecipientPhone("9999999999");order.setCurrency("INR");order.setTotalAmount(new BigDecimal("52.00"));
        var user=new EcommUser();user.setUserId("buyer-original");user.setUsername("Original buyer");user.setEmail("original@test.invalid");service.createOrder(order,user);
        user.setUsername("Changed buyer");user.setEmail("changed@test.invalid");service.createOrder(order,user);
        assertEquals(bodies.get(0),bodies.get(1));assertEquals("Original buyer",bodies.get(1).path("customer_details").path("customer_name").asText());assertEquals("original@test.invalid",bodies.get(1).path("customer_details").path("customer_email").asText());
    }
}
