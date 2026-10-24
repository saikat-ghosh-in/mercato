package com.mercato.Commerce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookAuthenticator {
    private WebhookAuthenticator() {}
    public static boolean valid(byte[] raw, String signature, String timestamp, String secret) {
        if(raw==null || signature==null || timestamp==null || !timestamp.matches("[0-9]{10,16}") || secret==null || secret.isBlank()) return false;
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(mac.doFinal(raw),Base64.getDecoder().decode(signature));
        } catch(Exception e) {return false;}
    }
}
