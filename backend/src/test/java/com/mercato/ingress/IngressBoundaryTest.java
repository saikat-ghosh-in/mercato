package com.mercato.ingress;
import com.mercato.Ingress.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class IngressBoundaryTest {
    private final String key="0123456789abcdef0123456789abcdef";
    private final ObjectMapper json=new ObjectMapper();
    private NetlifyOriginVerifier verifier(){return new NetlifyOriginVerifier(json,key,"site-id","https://shop.netlify.app",Set.of("production"));}
    private String signature(String site,long exp,String signingKey)throws Exception{
        var b64=Base64.getUrlEncoder().withoutPadding();
        String header=b64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body=b64.encodeToString(json.writeValueAsBytes(Map.of("iss","netlify","exp",exp,"netlify_id",site,"site_url","https://shop.netlify.app","deploy_context","production")));
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return header+"."+body+"."+b64.encodeToString(mac.doFinal((header+"."+body).getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void signatureAuthenticatesOnlyConfiguredSiteContextAndLifetime()throws Exception{
        assertTrue(verifier().valid(signature("site-id",Instant.now().getEpochSecond()+60,key)));
        assertFalse(verifier().valid(signature("other-site",Instant.now().getEpochSecond()+60,key)));
        assertFalse(verifier().valid(signature("site-id",Instant.now().getEpochSecond()-60,key)));
        assertFalse(verifier().valid(signature("site-id",Instant.now().getEpochSecond()+60,"other-signing-key-that-is-long-enough")));
        assertFalse(verifier().valid("invalid"));
    }
    @Test void originFilterRejectsDirectDuplicateAndMissingEdgeMetadata()throws Exception{
        var filter=new ProxyOriginFilter(verifier(),URI.create("https://shop.netlify.app"),false);
        var request=new MockHttpServletRequest("GET","/api/auth/session");var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->fail("Direct request reached application"));assertEquals(403,response.getStatus());
        request=new MockHttpServletRequest("GET","/api/auth/session");request.addHeader("X-Nf-Sign",signature("site-id",Instant.now().getEpochSecond()+60,key));response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->fail("Missing connection provenance reached application"));assertEquals(403,response.getStatus());
        request.addHeader("x-nf-sign",signature("site-id",Instant.now().getEpochSecond()+60,key));response=new MockHttpServletResponse();filter.doFilter(request,response,(a,b)->fail("Duplicate attestation reached application"));assertEquals(403,response.getStatus());
    }
    @Test void trustedRequestUsesCanonicalOriginAndEdgeIpWithoutTouchingRawBody()throws Exception{
        var filter=new ProxyOriginFilter(verifier(),URI.create("https://shop.netlify.app"),false);
        var r=new MockHttpServletRequest("POST","/api/public/cashfree/webhook-v2");r.addHeader("x-nf-sign",signature("site-id",Instant.now().getEpochSecond()+60,key));r.addHeader("x-mercato-client-ip","2001:db8::1");r.addHeader("x-mercato-ingress","edge-v1");r.addHeader("Forwarded","for=attacker;proto=http;host=attacker.test");r.addHeader("X-Forwarded-Host","attacker.test");r.setContent("{\"amount\": 12.00}\r\n".getBytes(StandardCharsets.UTF_8));
        filter.doFilter(r,new MockHttpServletResponse(),(request,response)->{var wrapped=(jakarta.servlet.http.HttpServletRequest)request;assertTrue(wrapped.isSecure());assertEquals("shop.netlify.app",wrapped.getServerName());assertEquals("2001:db8::1",wrapped.getRemoteAddr());assertNull(wrapped.getHeader("Forwarded"));assertNull(wrapped.getHeader("X-Forwarded-Host"));assertArrayEquals(r.getContentAsByteArray(),wrapped.getInputStream().readAllBytes());});
    }
    @Test void legacyExceptionIsExactAndNeverTrustsForwardedIp()throws Exception{
        var filter=new ProxyOriginFilter(verifier(),URI.create("https://shop.netlify.app"),true);
        var legacy=new MockHttpServletRequest("POST","/api/public/cashfree/webhook");legacy.setRemoteAddr("127.0.0.1");legacy.addHeader("X-Forwarded-For","8.8.8.8");
        filter.doFilter(legacy,new MockHttpServletResponse(),(request,response)->{var r=(jakarta.servlet.http.HttpServletRequest)request;assertEquals("127.0.0.1",r.getRemoteAddr());assertNull(r.getHeader("X-Forwarded-For"));});
        var other=new MockHttpServletRequest("POST","/api/public/cashfree/webhook/extra");var denied=new MockHttpServletResponse();filter.doFilter(other,denied,(a,b)->fail("Broad legacy exception"));assertEquals(403,denied.getStatus());
    }
}
