package com.mercato.order;
import com.mercato.Service.DelhiveryClient;
import com.mercato.Jobs.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DelhiveryClientTest {
    RestTemplate http=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    DelhiveryClient client=new DelhiveryClient(http,new ObjectMapper(),true,"synthetic-test-token",50);
    @Test void tokenAndBatchConfigurationAreValidatedWithoutExposingCredentials(){
        assertFalse(new DelhiveryClient(http,new ObjectMapper(),false,"",50).enabled());
        assertThrows(IllegalArgumentException.class,()->new DelhiveryClient(http,new ObjectMapper(),true,"",50));
        assertThrows(IllegalArgumentException.class,()->new DelhiveryClient(http,new ObjectMapper(),false,"",51));
        assertFalse(client.toString().contains("synthetic-test-token"));
    }
    @Test void fiftyOneWaybillsUseTwoStagingRequestsWithBackendAuthorization(){
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(DelhiveryClient.ENDPOINT))).andExpect(header("Authorization","Token synthetic-test-token"))
            .andExpect(request->{String query=request.getURI().getQuery();assertEquals(50,query.substring("waybill=".length()).split(",").length);})
            .andRespond(withSuccess("{\"ShipmentData\":[]}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(DelhiveryClient.ENDPOINT+"?waybill=050")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertTrue(client.fetch(IntStream.range(0,51).mapToObj(i->String.format("%03d",i)).toList()).updates().isEmpty());server.verify();
    }
    @Test void responseIdentityLeadingZerosReverseAndLocalTimeArePreserved(){
        server.expect(anything()).andRespond(withSuccess("""
          {"ShipmentData":[{"Shipment":{"AWB":"002","Status":{"Status":"Delivered","StatusType":"DL","StatusDateTime":"2026-10-29 12:00:00"},"IsReverse":true}},
          {"Shipment":{"AWB":"001","Status":{"Status":"Dispatched","StatusType":"UD","StatusDateTime":"2026-10-29T10:00:00Z"}}},
          {"Shipment":{"AWB":"foreign","Status":{"Status":"Delivered","StatusType":"DL"}}}]}
          """,MediaType.APPLICATION_JSON));
        var result=client.fetch(List.of("001","002"));assertEquals(Set.of("001","002"),result.updates().keySet());
        assertTrue(result.updates().get("002").reverse());assertEquals(Instant.parse("2026-10-29T06:30:00Z"),result.updates().get("002").providerAt());
        assertEquals("Dispatched",result.updates().get("001").status());server.verify();
    }
    @Test void explicitBatchRejectionFallsBackButAuthenticationDoesNot(){
        server.expect(anything()).andRespond(withBadRequest().body("{\"code\":\"BATCH_SIZE_EXCEEDED\"}"));
        server.expect(requestTo(DelhiveryClient.ENDPOINT+"?waybill=001")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(DelhiveryClient.ENDPOINT+"?waybill=002")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertTrue(client.fetch(List.of("001","002")).singleFallback());server.verify();server.reset();
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(ManualInterventionJobException.class,()->client.fetch(List.of("001","002")));server.verify();
    }
    @Test void rateLimitRetainsRetryAfterAndTransportCannotRunUnderLocks(){
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","17"));
        assertEquals(Duration.ofSeconds(17),assertThrows(WaitingJobException.class,()->client.fetch(List.of("001"))).retryAfter());server.verify();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThrows(IllegalStateException.class,()->client.fetch(List.of("001")));}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    @Test void arbitraryBadRequestDoesNotRetryAsSingles(){
        server.expect(anything()).andRespond(withBadRequest().body("{\"message\":\"Invalid waybill\"}"));
        assertThrows(ManualInterventionJobException.class,()->client.fetch(List.of("001","002")));server.verify();
        assertNull(DelhiveryClient.time("invalid"));assertEquals(Instant.parse("2026-10-29T10:00:00Z"),DelhiveryClient.time("2026-10-29T15:30:00+05:30"));
    }
}
