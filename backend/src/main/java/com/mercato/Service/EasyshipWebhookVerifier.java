package com.mercato.Service;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/** Easyship's documented JWT authenticates the sender, not the event body.
 * Events only wake a canonical authenticated tracking read. */
@Component
public class EasyshipWebhookVerifier {
    private final ObjectMapper json; private final Clock clock; private final boolean enabled; private final String secret;
    public EasyshipWebhookVerifier(ObjectMapper json,Clock clock,@Value("${EASYSHIP_WEBHOOK_ENABLED:false}") boolean enabled,@Value("${EASYSHIP_WEBHOOK_SECRET:}") String secret){
        if(enabled&&(secret==null||!secret.startsWith("webh_")||secret.length()<16))throw new IllegalArgumentException("An Easyship webhook secret is required");
        this.json=json;this.clock=clock;this.enabled=enabled;this.secret=secret;
    }
    public void verify(String signature){
        if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Tracking webhook is not configured");
        try{
            if(signature==null||signature.length()>8192)throw new IllegalArgumentException();
            var parts=signature.split("\\.",-1);if(parts.length!=3)throw new IllegalArgumentException();
            var decoder=Base64.getUrlDecoder();var header=json.readTree(decoder.decode(parts[0]));
            if(!"HS256".equals(header.path("alg").asText()))throw new IllegalArgumentException();
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            if(!MessageDigest.isEqual(mac.doFinal((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII)),decoder.decode(parts[2])))throw new IllegalArgumentException();
            var claims=json.readTree(decoder.decode(parts[1]));
            if(!claims.path("easyship_company_id").isTextual()||claims.path("easyship_company_id").asText().isBlank())throw new IllegalArgumentException();
            long now=clock.instant().getEpochSecond();
            for(String field:new String[]{"exp","nbf","iat"})if(claims.has(field)&&!claims.get(field).canConvertToLong())throw new IllegalArgumentException();
            if((claims.has("exp")&&claims.get("exp").asLong()<=now)||(claims.has("nbf")&&claims.get("nbf").asLong()>now+60)||(claims.has("iat")&&claims.get("iat").asLong()>now+60))throw new IllegalArgumentException();
        }catch(Exception invalid){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid tracking webhook signature");}
    }
}
