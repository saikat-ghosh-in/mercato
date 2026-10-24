package com.mercato.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Test-only browsers carry real Spring Session cookies and real CSRF bootstrap tokens. */
public final class CookieBrowserHarness {
    private static final String SELECTED=CookieBrowserHarness.class.getName()+".selected";
    private record Browser(Cookie cookie,String context,String csrf,String csrfHeader) {}
    private final MockMvc raw;
    private final ObjectMapper json;
    private final Map<String,Browser> browsers=new HashMap<>();
    private String defaultAnonymous;

    public CookieBrowserHarness(MockMvc raw,ObjectMapper json){this.raw=raw;this.json=json;}

    public String anonymous() throws Exception {
        var result=raw.perform(get("/api/auth/session")).andExpect(status().isOk()).andReturn();
        Cookie cookie=result.getResponse().getCookie("mercato-demo-session");
        if(cookie==null)throw new IllegalStateException("Session bootstrap did not issue the demo cookie");
        var session=json.readTree(result.getResponse().getContentAsString());
        var csrf=json.readTree(raw.perform(get("/api/auth/csrf").cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String handle="cookie-browser:"+UUID.randomUUID();
        browsers.put(handle,new Browser(cookie,session.get("contextId").asText(),csrf.get("token").asText(),csrf.get("headerName").asText()));
        return handle;
    }

    public String defaultAnonymous() throws Exception {
        if(defaultAnonymous==null)defaultAnonymous=anonymous();
        return defaultAnonymous;
    }

    public String login(String name,String password) throws Exception {return login(anonymous(),name,password);}

    public String login(String guestHandle,String name,String password) throws Exception {
        Browser before=browsers.get(guestHandle);
        if(before==null)throw new IllegalArgumentException("Unknown test browser");
        var result=raw.perform(post("/api/auth/signin").with(as(guestHandle)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",name,"password",password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andReturn();
        var session=json.readTree(result.getResponse().getContentAsString());
        Cookie rotated=result.getResponse().getCookie("mercato-demo-session");
        Cookie cookie=rotated==null?before.cookie():rotated;
        var csrf=json.readTree(raw.perform(get("/api/auth/csrf").cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String handle="cookie-browser:"+UUID.randomUUID();
        browsers.put(handle,new Browser(cookie,session.get("contextId").asText(),csrf.get("token").asText(),csrf.get("headerName").asText()));
        return handle;
    }

    public RequestPostProcessor as(String handle) {
        return request->{
            Browser browser=browsers.get(handle);
            if(browser==null)throw new IllegalArgumentException("Unknown test browser");
            request.setCookies(browser.cookie());
            request.setAttribute(SELECTED,handle);
            if(!Set.of("GET","HEAD","OPTIONS","TRACE").contains(request.getMethod())){
                request.removeHeader("X-Account-Context");request.addHeader("X-Account-Context",browser.context());
                request.removeHeader(browser.csrfHeader());request.addHeader(browser.csrfHeader(),browser.csrf());
            }
            return request;
        };
    }

    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return raw.perform(request.with(nativeRequest->{
            if(nativeRequest.getAttribute(SELECTED)!=null)return nativeRequest;
            try{return as(defaultAnonymous()).postProcessRequest(nativeRequest);}
            catch(Exception failure){throw new IllegalStateException("Unable to bootstrap anonymous test browser",failure);}
        }));
    }
}
