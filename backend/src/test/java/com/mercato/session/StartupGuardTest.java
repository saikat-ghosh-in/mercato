package com.mercato.session;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class StartupGuardTest {
    private static final AtomicBoolean BEAN_CREATED=new AtomicBoolean();
    @Configuration(proxyBeanMethods=false)
    static class Probe {
        @Bean Object migrationOrListenerProbe(){BEAN_CREATED.set(true);return new Object();}
    }
    @Test void unsafeProfilesAreRejectedBeforeAnyMigrationOrListenerBeanCanBeCreated(){
        BEAN_CREATED.set(false);
        var app=new SpringApplication(Probe.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setRegisterShutdownHook(false);
        app.setLogStartupInfo(false);
        var failure=assertThrows(IllegalStateException.class,()->app.run("--spring.profiles.active=prod,demo"));
        assertTrue(failure.getMessage().contains("Unsafe authentication configuration"));
        assertFalse(BEAN_CREATED.get());
    }
}
