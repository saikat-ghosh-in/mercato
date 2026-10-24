package com.mercato.Security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.util.Arrays;
public final class SafeEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder(){return Ordered.LOWEST_PRECEDENCE;}
    @Override public void postProcessEnvironment(ConfigurableEnvironment env,SpringApplication app){validate(env);}
    public static void validate(Environment env) {
        var profiles=Arrays.asList(env.getActiveProfiles());
        String stage=env.getProperty("app.env","").strip().toLowerCase(java.util.Locale.ROOT);
        boolean live=profiles.stream().anyMatch(p->p.equals("prod")||p.equals("staging")||p.equals("production")) || java.util.Set.of("prod","production","staging").contains(stage);
        boolean demo=profiles.contains("demo");
        boolean bypass=env.getProperty("mercato.auth.mfa-bypass",Boolean.class,false);
        if(live && (demo || profiles.contains("test") || bypass || "reviewer".equals(env.getProperty("app.admin.username")) || "ReviewOnly!2026".equals(env.getProperty("app.admin.password")))) throw new IllegalStateException("Unsafe authentication configuration in deployed environment");
        if(bypass && !demo) throw new IllegalStateException("MFA bypass requires the isolated demo profile");
        if(live) {
            validateDeployedIngress(env);
            try {
                if(java.util.Base64.getDecoder().decode(env.getProperty("mercato.auth.mfa-encryption-key","")).length!=32)
                    throw new IllegalArgumentException();
            } catch(IllegalArgumentException invalid) {
                throw new IllegalStateException("Deployed MFA requires a base64 encoded 256 bit encryption key");
            }
        }
        if(demo) {
            if(!env.getProperty("spring.datasource.url","").startsWith("jdbc:h2:mem:") || !loopbackHost(env.getProperty("server.address",""))) throw new IllegalStateException("Demo requires an in-memory database and loopback binding");
            for(String key:java.util.List.of("frontend.base.url","file.base.url","cashfree.base.url","cashfree.return.url","cashfree.notify.url")) if(!loopbackUrl(env.getProperty(key,""))) throw new IllegalStateException("Demo URLs must remain on loopback");
            for(String key:java.util.List.of("cashfree.app.id","cashfree.secret.key")) if(!"demo-disabled".equals(env.getProperty(key))) throw new IllegalStateException("Demo payment credentials must be disabled");
            for(String origin:env.getProperty("cors.allowed.origins","").split(",")) if(!loopbackUrl(origin.strip())) throw new IllegalStateException("Demo origins must remain on loopback");
        }
    }
    private static void validateDeployedIngress(Environment env) {
        if(!env.getProperty("mercato.ingress.enabled",Boolean.class,false)
                || !loopbackHost(env.getProperty("server.address",""))
                || env.getProperty("server.ssl.enabled",Boolean.class,false)
                || !"none".equalsIgnoreCase(env.getProperty("server.forward-headers-strategy","none"))) {
            throw new IllegalStateException("Deployed APIs require signed ingress, private loopback HTTP binding and explicit verified forwarding");
        }
        String[] keys=env.getProperty("mercato.ingress.keys","").split(",",-1);
        if(keys.length>2 || java.util.Arrays.stream(keys).anyMatch(key->key.strip().length()<32)
                || env.getProperty("mercato.ingress.site-id","").isBlank()
                || env.getProperty("mercato.ingress.contexts","production").isBlank()
                || !httpsOrigin(env.getProperty("mercato.ingress.site-url",""))
                || !httpsOrigin(env.getProperty("frontend.base.url",""))) {
            throw new IllegalStateException("Deployed APIs require complete signed ingress keys, site identity and canonical HTTPS origins");
        }
    }
    private static boolean httpsOrigin(String value) {
        try {
            URI uri=URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost()!=null && !loopbackHost(uri.getHost())
                    && uri.getRawUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null
                    && (uri.getRawPath().isEmpty()||"/".equals(uri.getRawPath()));
        } catch(Exception invalid){return false;}
    }    private static boolean loopbackHost(String host){return java.util.Set.of("127.0.0.1","localhost","::1","[::1]").contains(host);}
    private static boolean loopbackUrl(String value){try { URI u=URI.create(value); return java.util.Set.of("http","https").contains(u.getScheme()) && loopbackHost(u.getHost()); }catch(Exception e){return false;}}
}
