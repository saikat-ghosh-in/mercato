package com.mercato.Session;
import org.springframework.context.annotation.*;import org.springframework.core.env.Environment;import org.springframework.session.web.http.*;
@Configuration
public class SessionCookieConfig {
 @Bean public CookieSerializer cookieSerializer(Environment env){var cookie=new DefaultCookieSerializer();boolean demo=java.util.Arrays.asList(env.getActiveProfiles()).contains("demo");cookie.setCookieName(demo?"mercato-demo-session":"__Host-mercato-session");cookie.setUseSecureCookie(!demo);cookie.setUseHttpOnlyCookie(true);cookie.setSameSite("Lax");cookie.setCookiePath("/");return cookie;}
}
