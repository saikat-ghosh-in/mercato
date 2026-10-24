package com.mercato.Ingress;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;
import java.net.URI;
import java.util.*;
@Configuration
public class IngressConfiguration {
    @Bean @ConditionalOnProperty(name="mercato.ingress.enabled",havingValue="true")
    FilterRegistrationBean<ProxyOriginFilter> proxyOrigin(ObjectMapper json,@Value("${mercato.ingress.keys}")String keys,@Value("${mercato.ingress.site-id}")String siteId,@Value("${mercato.ingress.site-url}")String siteUrl,@Value("${mercato.ingress.contexts:production}")String contexts,@Value("${frontend.base.url}")String publicOrigin,@Value("${mercato.ingress.legacy-webhook-enabled:false}")boolean legacy){
        var verifier=new NetlifyOriginVerifier(json,keys,siteId,siteUrl,Set.copyOf(Arrays.asList(contexts.split(","))));
        var registration=new FilterRegistrationBean<>(new ProxyOriginFilter(verifier,URI.create(publicOrigin),legacy));registration.setOrder(Ordered.HIGHEST_PRECEDENCE+10);return registration;
    }
    @Bean @ConditionalOnProperty(name="mercato.ingress.enabled",havingValue="true")
    FilterRegistrationBean<ForwardedHeaderFilter> explicitForwarding(){var registration=new FilterRegistrationBean<>(new ForwardedHeaderFilter());registration.setOrder(Ordered.HIGHEST_PRECEDENCE+20);return registration;}
    @Bean FilterRegistrationBean<ApiHeadersFilter> apiHeaders(){var registration=new FilterRegistrationBean<>(new ApiHeadersFilter());registration.setOrder(Ordered.HIGHEST_PRECEDENCE+30);return registration;}
}
