package com.mercato.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Service.EasyshipWebhookVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EasyshipWebhookVerifierTest {
    final ObjectMapper json=new ObjectMapper();
    final String secret="webh_synthetic_test_secret_for_unit_checks";
    final Clock clock=Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"),ZoneOffset.UTC);
    String signature(String algorithm,Map<String,Object> claims,String key)throws Exception{
        var encoder=Base64.getUrlEncoder().withoutPadding();
        String body=encoder.encodeToString(json.writeValueAsBytes(Map.of("alg",algorithm,"typ","JWT")))+"."+encoder.encodeToString(json.writeValueAsBytes(claims));
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return body+"."+encoder.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void acceptsDocumentedCompanyClaimWithVerifiedHs256Signature()throws Exception{
        var verifier=new EasyshipWebhookVerifier(json,clock,true,secret);
        assertDoesNotThrow(()->verifier.verify(signature("HS256",Map.of("easyship_company_id","synthetic-company"),secret)));
    }
    @Test void rejectsMissingForgedWrongAlgorithmExpiredAndMissingCompanyClaims()throws Exception{
        var verifier=new EasyshipWebhookVerifier(json,clock,true,secret);
        var invalid=List.of("malformed",signature("none",Map.of("easyship_company_id","company"),secret),signature("HS256",Map.of("easyship_company_id","company"),"another-key"),signature("HS256",Map.of("easyship_company_id","company","exp",clock.instant().minusSeconds(300).getEpochSecond()),secret),signature("HS256",Map.of(),secret));
        assertThrows(ResponseStatusException.class,()->verifier.verify(null));
        for(String value:invalid)assertEquals(401,assertThrows(ResponseStatusException.class,()->verifier.verify(value)).getStatusCode().value());
    }
    @Test void disabledOrUnconfiguredReceiverFailsClosed(){
        assertThrows(IllegalArgumentException.class,()->new EasyshipWebhookVerifier(json,clock,true,""));
        assertEquals(503,assertThrows(ResponseStatusException.class,()->new EasyshipWebhookVerifier(json,clock,false,"").verify("anything")).getStatusCode().value());
    }
}
