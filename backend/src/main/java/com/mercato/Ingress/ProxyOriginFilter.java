package com.mercato.Ingress;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.*;
import java.util.*;

public final class ProxyOriginFilter extends OncePerRequestFilter {
    private final NetlifyOriginVerifier verifier;private final URI publicOrigin;private final boolean legacy;
    public ProxyOriginFilter(NetlifyOriginVerifier verifier,URI publicOrigin,boolean legacy){
        if(!"https".equals(publicOrigin.getScheme())||publicOrigin.getHost()==null||publicOrigin.getUserInfo()!=null||publicOrigin.getQuery()!=null||publicOrigin.getFragment()!=null||(publicOrigin.getPort()!=-1&&publicOrigin.getPort()!=443)||!(publicOrigin.getPath()==null||publicOrigin.getPath().isEmpty()||publicOrigin.getPath().equals("/")))throw new IllegalArgumentException("Canonical HTTPS public origin required");
        this.verifier=verifier;this.publicOrigin=publicOrigin;this.legacy=legacy;
    }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        String path=request.getRequestURI();
        boolean legacyAdapter=legacy&&((path.equals("/api/public/cashfree/webhook")&&request.getMethod().equals("POST"))||(path.equals("/api/public/cashfree/return")&&request.getMethod().equals("GET")));
        if(legacyAdapter){chain.doFilter(new CanonicalRequest(request,request.getRemoteAddr(),null),response);return;}
        String signature=single(request,"x-nf-sign"),ip=single(request,"x-mercato-client-ip");
        boolean owned=path.equals("/api")||path.startsWith("/api/")||path.equals("/images")||path.startsWith("/images/");
        if(!owned||!verifier.valid(signature)||!"edge-v1".equals(single(request,"x-mercato-ingress"))||!literalIp(ip)){
            response.setStatus(403);response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.getWriter().write("{\"code\":\"UNTRUSTED_INGRESS\"}");return;
        }
        chain.doFilter(new CanonicalRequest(request,ip,publicOrigin),response);
    }
    private static String single(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));return values.size()==1&&!values.get(0).contains(",")?values.get(0):null;}
    private static boolean literalIp(String ip){
        if(ip==null||ip.length()>45||!ip.matches("[0-9a-fA-F:.]+"))return false;
        if(ip.contains(":")){try{return InetAddress.getByName(ip)!=null;}catch(Exception invalid){return false;}}
        String[] pieces=ip.split("\\.",-1);if(pieces.length!=4)return false;for(String p:pieces){try{if(p.isEmpty()||p.length()>3||Integer.parseInt(p)>255)return false;}catch(NumberFormatException invalid){return false;}}return true;
    }
    private static class CanonicalRequest extends HttpServletRequestWrapper {
        private final String ip;private final URI origin;
        CanonicalRequest(HttpServletRequest request,String ip,URI origin){super(request);this.ip=ip;this.origin=origin;}
        private boolean hidden(String name){String n=name.toLowerCase(Locale.ROOT);return n.equals("forwarded")||n.startsWith("x-forwarded-")||n.equals("x-real-ip")||n.equals("true-client-ip")||n.equals("x-nf-client-connection-ip")||n.startsWith("x-mercato-")||n.equals("x-nf-sign");}
        public String getRemoteAddr(){return ip;}
        public String getRemoteHost(){return ip;}
        public String getScheme(){return origin==null?super.getScheme():"https";}
        public boolean isSecure(){return origin==null?super.isSecure():true;}
        public String getServerName(){return origin==null?super.getServerName():origin.getHost();}
        public int getServerPort(){return origin==null?super.getServerPort():origin.getPort()==-1?443:origin.getPort();}
        public String getHeader(String name){if(hidden(name))return null;if(origin!=null&&name.equalsIgnoreCase("host"))return origin.getRawAuthority();return super.getHeader(name);}
        public Enumeration<String> getHeaders(String name){if(hidden(name))return Collections.emptyEnumeration();if(origin!=null&&name.equalsIgnoreCase("host"))return Collections.enumeration(List.of(origin.getRawAuthority()));return super.getHeaders(name);}
        public Enumeration<String> getHeaderNames(){return Collections.enumeration(Collections.list(super.getHeaderNames()).stream().filter(n->!hidden(n)).toList());}
        public StringBuffer getRequestURL(){return origin==null?super.getRequestURL():new StringBuffer(origin.getScheme()+"://"+origin.getRawAuthority()+getRequestURI());}
    }
}
