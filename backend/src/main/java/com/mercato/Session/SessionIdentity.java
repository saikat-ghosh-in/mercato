package com.mercato.Session;
import java.io.Serializable;
import java.time.Instant;
public record SessionIdentity(String userId,long accessVersion,Instant authenticatedAt,Instant expiresAt,String mfaState) implements Serializable {
 public boolean privileged(){return "NONE".equals(mfaState)||"VERIFIED".equals(mfaState);}
 public SessionIdentity withMfa(String state){return new SessionIdentity(userId,accessVersion,authenticatedAt,expiresAt,state);}
}
