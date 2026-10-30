package com.mercato.Session;
import com.mercato.Security.services.UserDetailsImpl;
import jakarta.servlet.*;import jakarta.servlet.http.*;import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;import java.util.Set;
@Component @RequiredArgsConstructor
public class SessionBoundaryFilter extends OncePerRequestFilter {
 private final SessionService sessions;
 @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith("/api/")||("GET".equals(request.getMethod())&&"/api/public/cashfree/return".equals(request.getRequestURI()))||("POST".equals(request.getMethod())&&"/api/public/easyship/webhook".equals(request.getRequestURI()))||Set.of("/api/public/cashfree/webhook","/api/public/cashfree/webhook-v2").contains(request.getRequestURI());}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
  var user=sessions.currentUser(request);var id=sessions.identity(request);
  if(user!=null&&id.privileged()) {var principal=UserDetailsImpl.build(user);principal.setPassword(null);var auth=new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());SecurityContextHolder.getContext().setAuthentication(auth);}
  boolean mutation=!Set.of("GET","HEAD","OPTIONS","TRACE").contains(request.getMethod());
  boolean bootstrap=Set.of("/api/auth/session","/api/auth/csrf").contains(request.getRequestURI())&&"GET".equals(request.getMethod());
  if(!bootstrap && (mutation||request.getHeader("X-Account-Context")!=null) && !sessions.context(request).equals(request.getHeader("X-Account-Context"))){response.setStatus(409);response.setContentType("application/json");response.getWriter().write("{\"code\":\"SESSION_CONTEXT_CHANGED\",\"message\":\"Account context changed. Refresh the session before retrying.\"}");return;}
  if(user==null||id==null||!id.privileged())sessions.guest(request);
  chain.doFilter(request,response);
 }
}
