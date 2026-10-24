package com.mercato.Ingress;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
public final class ApiHeadersFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("X-Frame-Options","DENY");response.setHeader("Referrer-Policy","strict-origin-when-cross-origin");response.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=()");
        if(request.getRequestURI().startsWith("/api")){response.setHeader("Cache-Control","no-store");response.setHeader("CDN-Cache-Control","no-store");response.setHeader("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'; base-uri 'none'");}
        chain.doFilter(request,response);
    }
}
