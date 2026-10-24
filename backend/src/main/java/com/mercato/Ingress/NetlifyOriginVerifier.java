package com.mercato.Ingress;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Verifies proxy-origin evidence only. No request/IP/body claims are implied by this JWS. */
public final class NetlifyOriginVerifier {
    private final ObjectMapper json;private final List<String> keys;private final String siteId,siteUrl;private final Set<String> contexts;
    public NetlifyOriginVerifier(ObjectMapper json,String keys,String siteId,String siteUrl,Set<String> contexts){
        this.json=json.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.keys=Arrays.stream(keys.split(",")).map(String::strip).toList();this.siteId=siteId;this.siteUrl=siteUrl;this.contexts=Set.copyOf(contexts);
        if(this.keys.isEmpty()||this.keys.size()>2||this.keys.stream().anyMatch(k->k.length()<32)||siteId.isBlank()||siteUrl.isBlank()||contexts.isEmpty())throw new IllegalArgumentException("Complete signed proxy configuration is required");
    }
    public boolean valid(String value){
        if(value==null||value.length()>8192||!value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))return false;
        try{
            String[] parts=value.split("\\.");var decoder=Base64.getUrlDecoder();var header=json.readTree(decoder.decode(parts[0]));
            if(!"HS256".equals(header.path("alg").asText())||header.has("crit")||header.has("jku")||header.has("jwk"))return false;
            byte[] supplied=decoder.decode(parts[2]);boolean accepted=false;
            for(String key:keys){var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));accepted|=MessageDigest.isEqual(mac.doFinal((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII)),supplied);}
            if(!accepted)return false;
            var claims=json.readTree(decoder.decode(parts[1]));var expiry=claims.path("exp");
            return "netlify".equals(claims.path("iss").asText())&&expiry.isIntegralNumber()&&expiry.canConvertToLong()&&expiry.longValue()>Instant.now().getEpochSecond()-30
                &&siteId.equals(claims.path("netlify_id").asText())&&siteUrl.equals(claims.path("site_url").asText())&&contexts.contains(claims.path("deploy_context").asText());
        }catch(Exception failure){return false;}
    }
}
