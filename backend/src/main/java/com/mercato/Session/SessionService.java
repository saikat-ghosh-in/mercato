package com.mercato.Session;
import com.mercato.Entity.EcommUser;
import com.mercato.Repository.UserRepository;
import com.mercato.Security.jwt.JwtUtils;
import com.mercato.Mapper.EcommUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class SessionService {
 public static final String IDENTITY="mercato.identity", CONTEXT="mercato.context", GUEST="mercato.guest";
 private final UserRepository users;
 private final JwtUtils jwt;
 public String context(HttpServletRequest request) {
   var s=request.getSession(); String context=(String)s.getAttribute(CONTEXT);
   if(context==null){ context=UUID.randomUUID().toString(); s.setAttribute(CONTEXT,context); }
   return context;
 }
 public SessionIdentity identity(HttpServletRequest request){var s=request.getSession(false); return s==null?null:(SessionIdentity)s.getAttribute(IDENTITY);}
 @Transactional(readOnly=true)
 public EcommUser currentUser(HttpServletRequest request){
   var identity=identity(request); if(identity==null)return null;
   var user=users.findByUserId(identity.userId()).orElse(null);
   if(!identity.expiresAt().isAfter(Instant.now()) || user==null || !user.isEnabled() || user.isAccountLocked() || !user.isEmailVerified() || user.getAccessVersion()!=identity.accessVersion()) { request.getSession().invalidate(); return null; }
   user.getRoles().size(); return user;
 }
 public void establish(HttpServletRequest request,EcommUser user,String state){
   request.getSession(); request.changeSessionId(); var s=request.getSession();
   var roles=user.getRoles().stream().map(r->r.getRoleName().name()).collect(java.util.stream.Collectors.toSet());
   var policy=SessionPolicy.forRoles(roles); Instant now=Instant.now();
   s.setMaxInactiveInterval(policy.idleSeconds());
   s.setAttribute(IDENTITY,new SessionIdentity(user.getUserId(),user.getAccessVersion(),now,now.plusSeconds(policy.absoluteSeconds()),state));
   s.setAttribute(org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,user.getUserId());
   s.setAttribute(CONTEXT,UUID.randomUUID().toString());
   s.removeAttribute("SPRING_SECURITY_CONTEXT");
   s.removeAttribute("mercato.mfa.proved-at");
   s.removeAttribute("mercato.mfa.pending-seed");
 }
 public boolean recentMfa(HttpServletRequest request){var s=request.getSession(false);if(s==null)return false;var proof=s.getAttribute("mercato.mfa.proved-at");return proof instanceof Instant time && time.plusSeconds(300).isAfter(Instant.now());}
 public void completeMfa(HttpServletRequest request){var s=request.getSession();s.setAttribute("mercato.mfa.proved-at",Instant.now()); var id=identity(request); s.setAttribute(IDENTITY,id.withMfa("VERIFIED")); request.changeSessionId(); s.setAttribute(CONTEXT,UUID.randomUUID().toString());}
 public void guest(HttpServletRequest request){var s=request.getSession(); String token=(String)s.getAttribute(GUEST); if(token==null){token=jwt.generateGuestTokenValue();s.setAttribute(GUEST,token);}request.setAttribute(JwtUtils.GUEST_TOKEN_ATTRIBUTE,token);context(request);}
 @Transactional(readOnly=true)
 public Map<String,Object> bootstrap(HttpServletRequest request){
   var user=currentUser(request); var id=identity(request); String state=id==null?"NONE":id.mfaState(); boolean authenticated=user!=null&&id.privileged();
   Map<String,Object> result=new LinkedHashMap<>();result.put("authenticated",authenticated);result.put("user",authenticated?EcommUserMapper.toDTO(user):null);result.put("contextId",context(request));result.put("expiresAt",id==null?null:id.expiresAt());result.put("mfa",Map.of("state",state));return result;
 }
}
