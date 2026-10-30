package com.mercato.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Service.EasyshipClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Clock;

@Configuration
public class EasyshipConfig {
    @Bean EasyshipClient easyshipClient(ObjectMapper json,Clock clock,
            @Value("${EASYSHIP_ENABLED:false}")boolean enabled,
            @Value("${EASYSHIP_SANDBOX_TOKEN:}")String token,
            @Value("${EASYSHIP_TRACKING_BATCH_SIZE:50}")int batchSize){
        var factory=new SimpleClientHttpRequestFactory(){
            @Override protected void prepareConnection(HttpURLConnection connection,String method)throws IOException{
                super.prepareConnection(connection,method);connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(5000);factory.setReadTimeout(15000);
        return new EasyshipClient(new RestTemplate(factory),json,clock,enabled,token,batchSize);
    }
}
