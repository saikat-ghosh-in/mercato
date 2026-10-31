package com.mercato.commerce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Commerce.CashfreeGateway;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CashfreeGatewayEnvironmentTest {
    private CashfreeGateway gateway(String base){return new CashfreeGateway(new RestTemplate(),new ObjectMapper(),base,"test-id","test-secret","2023-08-01");}
    @Test void acceptsSandboxAndDisabledLoopbackDemoWithoutNetworkCalls(){
        assertDoesNotThrow(()->gateway("https://sandbox.cashfree.com/pg"));
        assertDoesNotThrow(()->gateway("https://sandbox.cashfree.com/pg/"));
        assertDoesNotThrow(()->gateway("http://127.0.0.1:9"));
    }
    @Test void rejectsProductionAndMalformedGatewayConfigurationBeforeAnyPaymentCanBeSent(){
        for(String url:new String[]{"https://api.cashfree.com/pg","http://sandbox.cashfree.com/pg","https://sandbox.cashfree.com.evil.test/pg","https://user:secret@sandbox.cashfree.com/pg","https://sandbox.cashfree.com/pg?redirect=other","https://sandbox.cashfree.com/other","not-a-url"})
            assertThrows(IllegalArgumentException.class,()->gateway(url),url);
    }
}
