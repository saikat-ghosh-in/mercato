package com.mercato.Commerce;

import com.fasterxml.jackson.databind.*;
import com.mercato.Jobs.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Network adapter only: never called with an open database transaction. */
@Component
public class CashfreeGateway {
    private final RestTemplate http;private final ObjectMapper json;private final String base,id,secret,version;
    public CashfreeGateway(RestTemplate http,ObjectMapper json,@Value("${cashfree.base.url}") String base,@Value("${cashfree.app.id}")String id,@Value("${cashfree.secret.key}")String secret,@Value("${cashfree.api.version}")String version){this.http=http;this.json=json;this.base=base;this.id=id;this.secret=secret;this.version=version;}
    public JsonNode get(String path){return call(HttpMethod.GET,path,null,null);}
    public JsonNode post(String path,Object body,String businessKey){return call(HttpMethod.POST,path,body,businessKey);}
    public JsonNode patch(String path,Object body,String businessKey){return call(HttpMethod.PATCH,path,body,businessKey);}
    private JsonNode call(HttpMethod method,String path,Object body,String key) {
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Gateway I/O inside database transaction");
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("x-client-id",id);headers.set("x-client-secret",secret);headers.set("x-api-version",version);
        if(key!=null)headers.set("x-idempotency-key",UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
        try {
            var response=http.exchange(base+path,method,new HttpEntity<>(body,headers),String.class);
            return json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(response.getBody());
        }catch(HttpStatusCodeException e){
            String code="";
            try{code=json.readTree(e.getResponseBodyAsString()).path("code").asText("");}catch(Exception ignored){}
            throw new GatewayFailure(e.getStatusCode().value(),code);
        }catch(ResourceAccessException e){throw new TransientJobException("Gateway outcome unknown; reconcile before retry",e);}
        catch(java.io.IOException e){throw new TransientJobException("Invalid gateway response; reconcile before retry",e);}
    }
    public static final class GatewayFailure extends RuntimeException {
        public final int status;public final String code;
        public GatewayFailure(int status,String code){super("Gateway request rejected");this.status=status;this.code=code;}
        public boolean missing(){return status==404;}
        public boolean temporary(){return status==429||status>=500;}
        public boolean insufficientFunds(){return Set.of("insufficient_balance","insufficient_funds","refund_insufficient_balance","refund_failed_insufficient_balance").contains(code.toLowerCase(Locale.ROOT));}
    }
}
