package com.mercato.session;

import com.mercato.Auth.*;
import com.mercato.Entity.*;
import com.mercato.Entity.Role;
import com.mercato.Jobs.DurableJobs;
import com.mercato.Repository.*;
import com.mercato.Security.jwt.JwtUtils;
import com.mercato.Service.*;
import com.mercato.Session.*;
import com.mercato.jobs.PostgresTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.*;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL locking/consumption evidence; the only rows are synthetic test accounts. */
@SpringBootTest(classes=PostgresAuthConsumptionTest.Config.class,properties={
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false","spring.flyway.enabled=false",
    "spring.session.jdbc.initialize-schema=always","mercato.jobs.enabled=false",
    "mercato.auth.mfa-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "frontend.base.url=http://127.0.0.1:5174",
    "spring.app.jwtSecret=synthetic-test-only-key-not-used-for-authentication",
    "spring.app.jwtExpirationMs=60000"})
@TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class PostgresAuthConsumptionTest {
    private static final String DATABASE=PostgresTestSupport.database();
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages={"com.mercato.Entity","com.mercato.Auth","com.mercato.Commerce","com.mercato.Jobs"})
    @EnableJpaRepositories(basePackages={"com.mercato.Repository","com.mercato.Auth","com.mercato.Commerce"})
    @Import({SessionService.class,MfaService.class,SeedCipher.class,PasswordResetServiceImpl.class})
    static class Config {
        @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder();}
        @Bean JwtUtils jwt(){return mock(JwtUtils.class);}
        @Bean CartService carts(){return mock(CartService.class);}
        @Bean DurableJobs jobs(){return mock(DurableJobs.class);}
        @Bean SessionRevocation revocation(FindByIndexNameSessionRepository<? extends Session> repository){
            return spy(new SessionRevocation(repository));
        }
    }
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties){
        properties.add("spring.datasource.url",()->DATABASE);
        properties.add("spring.datasource.username",PostgresTestSupport::username);
        properties.add("spring.datasource.password",PostgresTestSupport::password);
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @AfterAll static void cleanup(@Autowired ConfigurableApplicationContext context){context.close();PostgresTestSupport.cleanup();}
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired PasswordResetService resetService;
    @Autowired PasswordEncoder passwords;
    @Autowired MfaCredentialRepository credentials;
    @Autowired MfaService mfa;
    @Autowired SeedCipher cipher;
    @Autowired SessionService sessions;
    @Autowired SessionRevocation revocation;
    @Autowired FindByIndexNameSessionRepository<? extends Session> indexed;
    @Autowired PlatformTransactionManager transactions;

    private EcommUser account(AppRole role){
        return new TransactionTemplate(transactions).execute(status->{
            var assigned=roles.findByRoleName(role).orElseGet(()->roles.save(new Role(role)));
            String name="pg"+UUID.randomUUID().toString().substring(0,12);
            return users.saveAndFlush(EcommUser.builder().username(name).email(name+"@mercato.invalid")
                    .password(passwords.encode("OriginalPassword!2026")).emailVerified(true).enabled(true)
                    .phoneNumber(String.format(Locale.ROOT,"7%09d",new java.security.SecureRandom().nextInt(1000000000)))
                    .roles(new HashSet<>(Set.of(assigned))).build());
        });
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private String indexedSession(EcommUser account){
        Session created=indexed.createSession();
        created.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,account.getUserId());
        created.setAttribute(SessionService.IDENTITY,new SessionIdentity(account.getUserId(),account.getAccessVersion(),
                Instant.now(),Instant.now().plusSeconds(600),"NONE"));
        ((SessionRepository)indexed).save(created);
        return created.getId();
    }
    private List<Boolean> race(Callable<Boolean> consume)throws Exception{
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Boolean> contender=()->{ready.countDown();assertTrue(start.await(5,TimeUnit.SECONDS));return consume.call();};
            var first=pool.submit(contender);var second=pool.submit(contender);
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }finally{start.countDown();pool.shutdownNow();}
    }
    @Test void resetTokenHasOneWinnerAndRevokesEveryIndexedSessionExactlyOnce()throws Exception{
        var account=account(AppRole.ROLE_USER);
        var token=resets.saveAndFlush(PasswordResetToken.builder().user(account).expiresAt(Instant.now().plusSeconds(600)).build());
        String first=indexedSession(account),second=indexedSession(account);
        assertEquals(2,indexed.findByPrincipalName(account.getUserId()).size());
        clearInvocations(revocation);
        var outcomes=race(()->{
            try{resetService.resetPassword(token.getToken(),"ChangedPassword!2026");return true;}
            catch(com.mercato.ExceptionHandler.CustomBadRequestException consumed){return false;}
        });
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());
        var changed=users.findByUserId(account.getUserId()).orElseThrow();
        assertEquals(1,changed.getAccessVersion());assertTrue(passwords.matches("ChangedPassword!2026",changed.getPassword()));
        assertTrue(resets.findById(token.getId()).orElseThrow().isUsed());
        assertTrue(indexed.findByPrincipalName(account.getUserId()).isEmpty());
        assertNull(indexed.findById(first));assertNull(indexed.findById(second));
        verify(revocation,times(1)).revokeAll(account.getUserId());
    }
    @Test void recoveryCodeHasOneWinnerAcrossIndependentAdministratorSessions()throws Exception{
        var account=account(AppRole.ROLE_ADMIN);
        var credential=new MfaCredential();credential.setUserId(account.getUserId());
        credential.setEncryptedSeed(cipher.encrypt(account.getUserId(),Totp.secret()));
        credential.getRecoveryHashes().add(AuthHash.digest(account.getUserId()+":SYNTHETIC-SINGLE-USE-CODE"));
        credentials.saveAndFlush(credential);
        var successfulSessions=new java.util.concurrent.atomic.AtomicInteger();
        var outcomes=race(()->{
            var request=new MockHttpServletRequest();sessions.establish(request,account,"CHALLENGE_REQUIRED");
            try{
                var result=mfa.verify(request,"SYNTHETIC-SINGLE-USE-CODE",true);
                if(Boolean.TRUE.equals(result.get("authenticated"))&&"VERIFIED".equals(sessions.identity(request).mfaState()))successfulSessions.incrementAndGet();
                return true;
            }catch(AuthProofException consumed){assertEquals("CHALLENGE_REQUIRED",sessions.identity(request).mfaState());return false;}
        });
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());assertEquals(1,successfulSessions.get());
        assertTrue(credentials.findById(account.getUserId()).orElseThrow().getRecoveryHashes().isEmpty());
    }
}
