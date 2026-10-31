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
    @Test void sandboxGenerationUsesOrderDeliveryContactsAndReturnsBothTrackingReferences(){
        var generator=new EasyshipClient(http,new ObjectMapper(),clock,true,"sand_synthetic_test_only",50,"8f43c1e2-dec6-40b2-aa65-bdcfbb72e1c2");
        server.expect(requestTo("https://public-api-sandbox.easyship.com/2024-09/trackings")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization","Bearer sand_synthetic_test_only"))
            .andExpect(request->{String body=request.getBody().toString();var destination=new ObjectMapper().readTree(body).path("destination_address");assertEquals("buyer@test.invalid",destination.path("contact_email").asText());assertEquals("Buyer",destination.path("contact_name").asText());assertEquals("+919999999999",destination.path("contact_phone").asText());assertEquals("700001",destination.path("postal_code").asText());assertEquals("IN",destination.path("country_alpha2").asText());assertEquals("Saved delivery address",destination.path("line_1").asText());assertFalse(body.contains("buy_label"));})
            .andRespond(withSuccess("{\"tracking\":{\"tracking_number\":\"MCT123\",\"easyship_shipment_id\":\"ESIN10000001\"}}",MediaType.APPLICATION_JSON));
        var generated=generator.createTracking("SANDBOX-123","MCT123",new EasyshipClient.Destination("Buyer","9999999999","buyer@test.invalid","Saved delivery address",null,"Kolkata","West Bengal","700001"));assertEquals("MCT123",generated.waybill());assertEquals("ESIN10000001",generated.reference());server.verify();
    }
    @Test void uncertainGenerationCanBeReconciledByItsOwnDeterministicReference(){
        server.expect(anything()).andExpect(queryParam("platform_order_number","SANDBOX-123"))
            .andRespond(withSuccess("{\"shipments\":[{\"platform_order_number\":\"SANDBOX-123\",\"easyship_shipment_id\":\"ESIN10000001\",\"trackings\":[{\"tracking_number\":\"MCT123\"}]}]}",MediaType.APPLICATION_JSON));
        assertEquals("ESIN10000001",client.findPrepared("SANDBOX-123","MCT123").reference());server.verify();
    }
    @Test void generationPreservesSimulatorIdentityAndCourierName(){
        var generator=new EasyshipClient(http,new ObjectMapper(),clock,true,"sand_synthetic_test_only",50,"8f43c1e2-dec6-40b2-aa65-bdcfbb72e1c2");
        server.expect(anything()).andRespond(withSuccess("""
            {"tracking":{"id":"9fd75c1b-144f-40f9-bad0-456fb64d3c09","tracking_number":"MCT123","easyship_shipment_id":"ESIN10000001","courier_service":{"name":"Sandbox Courier"}}}
            """,MediaType.APPLICATION_JSON));
        var prepared=generator.createTracking("SANDBOX-123","MCT123",new EasyshipClient.Destination("Buyer","9999999999","buyer@test.invalid","Saved address",null,"City","State","123456"));
        var result=new ObjectMapper().valueToTree(prepared);
        assertEquals("9fd75c1b-144f-40f9-bad0-456fb64d3c09",result.path("trackingId").asText());
        assertEquals("Sandbox Courier",result.path("courierName").asText());server.verify();
    }
    @Test void simulatorUsesOnlySandboxPatchAndNeverAcceptsArbitraryStatuses(){
        var id=UUID.fromString("9fd75c1b-144f-40f9-bad0-456fb64d3c09");
        assertThrows(IllegalArgumentException.class,()->client.simulate(id,"unknown"));
        server.expect(requestTo("https://public-api-sandbox.easyship.com/2024-09/sandbox/trackings/"+id)).andExpect(method(HttpMethod.PATCH)).andExpect(content().json("{\"status_slug\":\"in_transit_to_customer\"}"))
            .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        client.simulate(id,"in_transit_to_customer");server.verify();
    }
    @Test void paginatedMetadataMatchesBothReferencesAndDisplaysCourierUmbrellaName(){
        server.expect(requestTo("https://public-api-sandbox.easyship.com/2024-09/trackings?per_page=100&page=2"))
            .andRespond(withSuccess("{\"trackings\":[{\"id\":\"9fd75c1b-144f-40f9-bad0-456fb64d3c09\",\"easyship_shipment_id\":\"ESIN10000001\",\"tracking_number\":\"MCT123\",\"courier_service\":{\"umbrella_name\":\"SFExpress\"}}]}",MediaType.APPLICATION_JSON));
        var page=client.trackingMetadata("ESIN10000001","MCT123",2);
        assertEquals("SFExpress",page.tracking().courierName());assertNotNull(page.tracking().trackingId());assertFalse(page.hasMore());server.verify();
    }
}
