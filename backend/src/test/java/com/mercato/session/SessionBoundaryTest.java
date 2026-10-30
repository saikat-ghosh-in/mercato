package com.mercato.session;
import com.mercato.Session.*;
import com.mercato.Repository.UserRepository;
import com.mercato.Security.jwt.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SessionBoundaryTest {
 @Test void trackingWebhookPostSkipsBrowserContextButOtherMethodsDoNot() throws Exception {
  var service=mock(SessionService.class);var filter=new SessionBoundaryFilter(service);
  var post=new MockHttpServletRequest("POST","/api/public/easyship/webhook");var chain=new MockFilterChain();
  filter.doFilter(post,new MockHttpServletResponse(),chain);assertSame(post,chain.getRequest());verifyNoInteractions(service);
  when(service.context(any())).thenReturn("browser-context");
  var response=new MockHttpServletResponse();var patchChain=new MockFilterChain();
  filter.doFilter(new MockHttpServletRequest("PATCH","/api/public/easyship/webhook"),response,patchChain);assertEquals(409,response.getStatus());assertNull(patchChain.getRequest());
 }
 @Test void anonymousCartMutationRejectsStaleAccountContext() throws Exception {
  var service=new SessionService(mock(UserRepository.class),mock(JwtUtils.class));
  var filter=new SessionBoundaryFilter(service);
  var request=new MockHttpServletRequest("POST","/api/cart/add");request.getSession().setAttribute(SessionService.CONTEXT,"right");request.addHeader("X-Account-Context","stale");
  var response=new MockHttpServletResponse(); var chain=new MockFilterChain();filter.doFilter(request,response,chain);
  assertEquals(409,response.getStatus());assertTrue(response.getContentAsString().contains("SESSION_CONTEXT_CHANGED"));assertNull(chain.getRequest());
 }
 @Test void legacyBearerDoesNotAuthenticateOrReplaceServerGuestIdentity() throws Exception {
  var jwt=mock(JwtUtils.class);when(jwt.generateGuestTokenValue()).thenReturn("server-guest");
  var filter=new SessionBoundaryFilter(new SessionService(mock(UserRepository.class),jwt));
  var request=new MockHttpServletRequest("GET","/api/cart");request.addHeader("Authorization","Bearer legacy-auth");
  var response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());
  assertEquals("server-guest",request.getAttribute(JwtUtils.GUEST_TOKEN_ATTRIBUTE));assertNull(response.getHeader("X-Guest-Token"));
 }
 @Test void privateReadFromOldAccountIsFencedBeforeDataCanBeReturned() throws Exception {
  var service=new SessionService(mock(UserRepository.class),mock(JwtUtils.class));
  var filter=new SessionBoundaryFilter(service);var request=new MockHttpServletRequest("GET","/api/orders");request.getSession().setAttribute(SessionService.CONTEXT,"new-account");request.addHeader("X-Account-Context","old-account");
  var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request,response,chain);assertEquals(409,response.getStatus());assertNull(chain.getRequest());
 }
}
