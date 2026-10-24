package com.mercato.session;
import com.mercato.Security.services.CorsFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
class CorsBoundaryTest {
 @Test void preflightCannotGrantCrossOriginAccessOrSkipLaterIngressAuthentication()throws Exception{
  var filter=new CorsFilter();
  var request=new MockHttpServletRequest("OPTIONS","/api/user/addresses/example/default");request.addHeader("Origin","http://127.0.0.1:5175");request.addHeader("Access-Control-Request-Method","PATCH");
  var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request,response,chain);
  assertSame(request,chain.getRequest());assertNull(response.getHeader("Access-Control-Allow-Origin"));assertNull(response.getHeader("Access-Control-Allow-Methods"));assertNull(response.getHeader("Access-Control-Expose-Headers"));
 }
}
