package com.mercato.Auth;
import com.mercato.Security.jwt.JwtUtils;import com.mercato.Service.CartService;import com.mercato.Session.*;import jakarta.servlet.http.HttpServletRequest;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.Instant;
@Service @RequiredArgsConstructor
public class LegacyGuestMigrationService {
 private final JwtUtils jwt;private final GuestMigrationRepository claims;private final SessionService sessions;private final CartService carts;
 @Transactional public void migrate(HttpServletRequest request,String token){
  try{if(token==null||!jwt.validateJwtToken(token)||!"guest".equals(jwt.getClaimFromToken(token,"type")))throw new AuthProofException();}catch(RuntimeException e){throw new AuthProofException();}
  var claim=new GuestMigration();claim.setTokenHash(AuthHash.digest(token));claim.setClaimedAt(Instant.now());if(claims.existsById(claim.getTokenHash()))throw new AuthProofException();claims.saveAndFlush(claim);
  var id=sessions.identity(request);if(id!=null&&id.privileged())carts.mergeGuestCartOnLogin(id.userId(),token);else request.getSession().setAttribute(SessionService.GUEST,token);
  request.changeSessionId();request.getSession().setAttribute(SessionService.CONTEXT,java.util.UUID.randomUUID().toString());
 }
}
