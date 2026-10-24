package com.mercato.session;

import com.mercato.Session.SessionPolicy;
import com.mercato.Auth.Totp;
import com.mercato.Security.SafeEnvironmentGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.Instant;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SessionSecurityTest {
    @Test void strictestRoleControlsIdleAndAbsoluteExpiry() {
        var policy = SessionPolicy.forRoles(Set.of("ROLE_USER", "ROLE_SELLER", "ROLE_ADMIN"));
        assertEquals(1800, policy.idleSeconds());
        assertEquals(43200, policy.absoluteSeconds());
        assertEquals(86400, SessionPolicy.forRoles(Set.of("ROLE_USER")).idleSeconds());
        assertEquals(3600, SessionPolicy.forRoles(Set.of("ROLE_SELLER")).idleSeconds());
    }
    @Test void totpMatchesPublishedRfcVectorAndRejectsMalformedCodes() {
        assertEquals("287082", Totp.code("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1));
        assertEquals(1, Totp.match("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082", Instant.ofEpochSecond(59)));
        assertEquals(-1, Totp.match("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "123", Instant.ofEpochSecond(59)));
    }
    @Test void stagingCannotActivateDemoThroughAnEnvironmentAlias() {
        var env = new MockEnvironment().withProperty("app.env", "staging");
        env.setActiveProfiles("demo");
        assertThrows(IllegalStateException.class, () -> SafeEnvironmentGuard.validate(env));
    }
    @Test void demoRequiresMemoryDatabaseAndLoopbackBinding() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:file:./unsafe")
                .withProperty("server.address", "127.0.0.1");
        env.setActiveProfiles("demo");
        assertThrows(IllegalStateException.class, () -> SafeEnvironmentGuard.validate(env));
    }
    @Test void deployedEnvironmentCannotStartWithoutSignedPrivateIngressConfiguration() {
        var env=new MockEnvironment().withProperty("app.env","staging");
        assertThrows(IllegalStateException.class,()->SafeEnvironmentGuard.validate(env));
    }
    private MockEnvironment deployed(){return new MockEnvironment().withProperty("app.env","staging")
        .withProperty("mercato.ingress.enabled","true").withProperty("server.address","127.0.0.1")
        .withProperty("mercato.ingress.keys","abcdefghijklmnopqrstuvwxyz0123456789")
        .withProperty("mercato.ingress.site-id","test-site-id").withProperty("mercato.ingress.site-url","https://mercato-stage.netlify.app")
        .withProperty("frontend.base.url","https://mercato-stage.netlify.app")
        .withProperty("mercato.auth.mfa-encryption-key","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");}
    @Test void deployedMfaKeyIsValidatedBeforeAnyDatabaseMigration(){
        for(String invalid:java.util.List.of("","not-base64","c2hvcnQ="))
            assertThrows(IllegalStateException.class,()->SafeEnvironmentGuard.validate(deployed().withProperty("mercato.auth.mfa-encryption-key",invalid)));
    }
    @Test void completePrivateHttpsIngressConfigurationIsAccepted(){assertDoesNotThrow(()->SafeEnvironmentGuard.validate(deployed()));}
    @Test void unsafeTransportOverridesCannotReachMigrations(){
        for(var override:java.util.Map.of("server.address","0.0.0.0","mercato.ingress.keys","", "mercato.ingress.site-id","", "frontend.base.url","http://mercato-stage.netlify.app", "server.forward-headers-strategy","framework", "server.ssl.enabled","true").entrySet())
            assertThrows(IllegalStateException.class,()->SafeEnvironmentGuard.validate(deployed().withProperty(override.getKey(),override.getValue())));
    }
}
