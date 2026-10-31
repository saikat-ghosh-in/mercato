package com.mercato.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Service.EasyshipClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.time.Clock;

@Configuration
public class EasyshipConfig {
    @Bean EasyshipClient easyshipClient(ObjectMapper json,Clock clock,
            @Value("${EASYSHIP_ENABLED:false}")boolean enabled,
            @Value("${EASYSHIP_SANDBOX_TOKEN:}")String token,
            @Value("${EASYSHIP_TRACKING_BATCH_SIZE:50}")int batchSize,
            @Value("${EASYSHIP_SANDBOX_COURIER_ID:}")String courier){
        var http=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        var factory=new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(15000);
        return new EasyshipClient(new RestTemplate(factory),json,clock,enabled,token,batchSize,courier);
    }
}
