package com.mercato.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Service.EasyshipClient;
import com.mercato.Jobs.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EasyshipClientTest {
    final RestTemplate http=new RestTemplate();
    final MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    final Clock clock=Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"),ZoneOffset.UTC);
    final EasyshipClient client=new EasyshipClient(http,new ObjectMapper(),clock,true,"sand_synthetic_test_only",50);

    String response(String id,String awb,String status,String checkpointStatus,String at){return """
        {"shipments":[{"easyship_shipment_id":"%s","status":"%s","trackings":[{"tracking_number":"%s","leg_number":1}],
        "checkpoints":[{"primary_status":"%s","message":"Courier update","checkpoint_time":"%s"}]}],"meta":{"pagination":{"next":null}}}
        """.formatted(id,status,awb,checkpointStatus,at);}
    @Test void configurationRejectsProductionTokensAndNeverExposesSecrets(){
        assertFalse(new EasyshipClient(http,new ObjectMapper(),clock,false,"",50).enabled());
        for(String token:List.of("","prod_not_allowed"," sand_not_allowed","sand_"))
            assertThrows(IllegalArgumentException.class,()->new EasyshipClient(http,new ObjectMapper(),clock,true,token,50));
        assertThrows(IllegalArgumentException.class,()->new EasyshipClient(http,new ObjectMapper(),clock,false,"",101));
        assertFalse(client.toString().contains("sand_synthetic"));
    }
    @Test void sandboxBearerRequestMatchesBothShipmentAndTrackingIdentity(){
        server.expect(requestTo("https://public-api-sandbox.easyship.com/2024-09/shipments/trackings?easyship_shipment_id=ESIN10000001&include_checkpoints=true&per_page=100"))
            .andExpect(header("Authorization","Bearer sand_synthetic_test_only"))
            .andExpect(header("User-Agent","Mercato-Sandbox/1.0"))
            .andRespond(withSuccess(response("ESIN10000001","001234","Delivered","Delivered","2026-10-30T09:00:00Z"),MediaType.APPLICATION_JSON));
        var update=client.fetch(List.of("ESIN10000001")).updates().get("ESIN10000001");
        assertEquals("001234",update.waybill());assertEquals("DL",update.statusType());assertEquals(Instant.parse("2026-10-30T09:00:00Z"),update.providerAt());server.verify();
    }
    @Test void missingAmbiguousForeignOrUnprovenDeliveryCannotCompleteAParcel(){
        String one=response("ESIN10000001","001234","Delivered","Out For Delivery","2026-10-30T09:00:00Z");
        server.expect(anything()).andRespond(withSuccess(one,MediaType.APPLICATION_JSON));
        assertNull(client.fetch(List.of("ESIN10000001")).updates().get("ESIN10000001").providerAt());server.verify();
        server.reset();
        var other=new EasyshipClient(http,new ObjectMapper(),clock,true,"sand_synthetic",50);
        server.expect(anything()).andRespond(withSuccess(response("ESIN99999999","foreign","Delivered","Delivered","2026-10-30T09:00:00Z"),MediaType.APPLICATION_JSON));
        assertTrue(other.fetch(List.of("ESIN10000001")).updates().isEmpty());server.verify();
    }
    @Test void reverseDeliveryAndNaiveTimestampsNeverSupplyForwardDeliveryProof(){
        server.expect(anything()).andRespond(withSuccess(response("ESIN10000001","001234","Returned To Sender","Delivered","2026-10-30T09:00:00"),MediaType.APPLICATION_JSON));
        var update=client.fetch(List.of("ESIN10000001")).updates().get("ESIN10000001");
        assertTrue(update.reverse());assertNull(update.providerAt());server.verify();
    }
    @Test void returnHistoryBlocksForwardDeliveryEvenWhenTheReturnTimestampIsInvalid(){
        String body=response("ESIN10000001","001234","Delivered","Delivered","2026-10-30T09:00:00Z")
            .replace("\"checkpoints\":[","\"checkpoints\":[{\"primary_status\":\"Exception\",\"message\":\"Returned To Sender\",\"checkpoint_time\":\"invalid\"},");
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertTrue(client.fetch(List.of("ESIN10000001")).updates().get("ESIN10000001").reverse());server.verify();
    }
    @Test void duplicateShipmentOrMultipleTrackingLegsRemainAmbiguous(){
        String duplicate="{\"shipments\":[{\"easyship_shipment_id\":\"ESIN10000001\"},{\"easyship_shipment_id\":\"ESIN10000001\"}]}";
        server.expect(anything()).andRespond(withSuccess(duplicate,MediaType.APPLICATION_JSON));
        assertTrue(client.fetch(List.of("ESIN10000001")).updates().isEmpty());server.verify();
    }
    @Test void rateLimitsAreBoundedAndCarrierErrorsAreSanitized(){
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","40").body("private-provider-error"));
        var failure=assertThrows(WaitingJobException.class,()->client.fetch(List.of("ESIN10000001")));
        assertEquals(Duration.ofSeconds(40),failure.retryAfter());assertFalse(failure.getMessage().contains("private"));server.verify();
        assertThrows(WaitingJobException.class,()->client.fetch(List.of("ESIN10000001")));
    }
    @Test void invalidReferencesAndTransactionsNeverMakeNetworkRequests(){
        assertThrows(IllegalArgumentException.class,()->client.fetch(List.of("../../foreign")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThrows(IllegalStateException.class,()->client.fetch(List.of("ESIN10000001")));}
        finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    @Test void permissionFailuresDoNotFallBackToProductionOrLeakProviderBodies(){
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("private-provider-error"));
        var failure=assertThrows(ManualInterventionJobException.class,()->client.fetch(List.of("ESIN10000001")));
        assertFalse(failure.getMessage().contains("private-provider-error"));server.verify();
    }
}
