package com.mercato.Auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
@Component
public final class SeedCipher {
 private final String configuredKey;
 public SeedCipher(@Value("${mercato.auth.mfa-encryption-key:}") String key){this.configuredKey=key;}
 private SecretKeySpec key(){try{byte[] raw=Base64.getDecoder().decode(configuredKey);if(raw.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(raw,"AES");}catch(Exception e){throw new IllegalStateException("MFA encryption key must be a base64 encoded 256 bit key");}}
 public String encrypt(String userId,String plaintext){try{byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));c.updateAAD(userId.getBytes(StandardCharsets.UTF_8));byte[] encoded=c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(java.nio.ByteBuffer.allocate(iv.length+encoded.length).put(iv).put(encoded).array());}catch(Exception e){throw new IllegalStateException("MFA encryption unavailable",e);}}
 public String decrypt(String userId,String ciphertext){try{byte[] raw=Base64.getDecoder().decode(ciphertext);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOf(raw,12)));c.updateAAD(userId.getBytes(StandardCharsets.UTF_8));return new String(c.doFinal(java.util.Arrays.copyOfRange(raw,12,raw.length)),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("MFA seed authentication failed",e);}}
}
