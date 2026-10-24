package com.mercato.Auth;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
public final class Totp {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp() {}
    public static String secret() {
        byte[] bytes=new byte[20]; new SecureRandom().nextBytes(bytes);
        StringBuilder out=new StringBuilder(); int bits=0,value=0;
        for(byte b:bytes) { value=(value<<8)|(b&255); bits+=8; while(bits>=5) { bits-=5; out.append(ALPHABET.charAt((value>>bits)&31)); } }
        return out.toString();
    }
    private static byte[] decode(String value) {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); int bits=0,buffer=0;
        for(char c:value.toCharArray()) { int n=ALPHABET.indexOf(c); if(n<0) throw new IllegalArgumentException("Invalid TOTP seed"); buffer=(buffer<<5)|n; bits+=5; if(bits>=8){ bits-=8; out.write((buffer>>bits)&255); } }
        return out.toByteArray();
    }
    public static String code(String secret,long step) {
        try { Mac mac=Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(decode(secret),"HmacSHA1"));
            byte[] h=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array()); int o=h[h.length-1]&15;
            int n=((h[o]&127)<<24)|((h[o+1]&255)<<16)|((h[o+2]&255)<<8)|(h[o+3]&255);
            return String.format(java.util.Locale.ROOT,"%06d",n%1000000);
        } catch(java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static long match(String secret,String candidate,Instant now) {
        if(candidate==null || !candidate.matches("[0-9]{6}")) return -1;
        long step=now.getEpochSecond()/30;
        for(long s=step-1;s<=step+1;s++) if(java.security.MessageDigest.isEqual(code(secret,s).getBytes(java.nio.charset.StandardCharsets.US_ASCII),candidate.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) return s;
        return -1;
    }
}
