package com.mercato.session;
import com.fasterxml.jackson.databind.*;import com.mercato.Auth.*;import com.mercato.Repository.UserRepository;import com.mercato.Session.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.web.servlet.*;import jakarta.servlet.http.Cookie;import org.springframework.http.MediaType;
import java.util.*;import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:mercato-session-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","mercato.auth.mfa-bypass=false","mercato.auth.mfa-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","marketplace.notices.initial-delay-ms=3600000","spring.session.jdbc.initialize-schema=always","spring.flyway.enabled=false","spring.jpa.open-in-view=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class CookieSessionIntegrationTest {
 @Autowired com.mercato.Jobs.JobStore jobStore;
 @Autowired com.mercato.Mail.AuthenticationMailGuard mailGuard;
 @Test void stepUpMailIsSkippedAfterChallengeExpiryReplacementOrSessionRevocation()throws Exception{
  var account=users.findByUsername("shopperreview").orElseThrow();String id=account.getUserId();
  try{
   for(int i=0;i<50;i++){var failure=new LoginFailure();failure.setId(UUID.randomUUID().toString());failure.setAccountHash(AuthHash.digest(account.getUsername()));failure.setFailedAt(Instant.now());failures.save(failure);}
   var b=browser();b=update(b,postJson(b,"/api/auth/signin",Map.of("username",account.getUsername(),"password","ShopOnly!2026")).andExpect(status().isOk()).andReturn());
   postJson(b,"/api/auth/step-up/send",Map.of()).andExpect(status().isOk());
   UUID first=jdbc.queryForObject("SELECT id FROM durable_jobs WHERE business_key LIKE 'step-up:%' AND email_recipient=? ORDER BY created_at DESC LIMIT 1",UUID.class,account.getEmail());
   assertTrue(mailGuard.current(jobStore.get(first)));
   postJson(b,"/api/auth/step-up/send",Map.of()).andExpect(status().isOk());
   assertFalse(mailGuard.current(jobStore.get(first)));
   var current=jdbc.queryForObject("SELECT id FROM durable_jobs WHERE business_key LIKE 'step-up:%' AND email_recipient=? AND id<>? ORDER BY created_at DESC LIMIT 1",UUID.class,account.getEmail(),first);
   assertTrue(mailGuard.current(jobStore.get(current)));
   jdbc.update("UPDATE auth_email_challenges SET expires_at=? WHERE user_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),id);
   assertFalse(mailGuard.current(jobStore.get(current)));
   jdbc.update("UPDATE auth_email_challenges SET expires_at=? WHERE user_id=?",java.sql.Timestamp.from(Instant.now().plusSeconds(300)),id);
   assertTrue(mailGuard.current(jobStore.get(current)));
   revocation.revokeAll(id);assertFalse(mailGuard.current(jobStore.get(current)));
  }finally{failures.deleteAll();emailChallenges.deleteById(id);}
 }
 @Test void verificationStatusIsBoundToTheRegistrationBrowserAndDoesNotEnumerateEmails()throws Exception{
  var owner=browser();String unique="mail"+UUID.randomUUID().toString().substring(0,8);
  postJson(owner,"/api/auth/register",Map.of("username",unique,"email",unique+"@mercato.invalid","password","TestingOnly!2026","phoneNumber",String.format(java.util.Locale.ROOT,"6%09d",new java.security.SecureRandom().nextInt(1000000000)),"firstName","Mail","lastName","Test" )).andExpect(status().isOk());
  mvc.perform(get("/api/auth/verification-delivery").cookie(owner.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("QUEUED"));
  jdbc.update("UPDATE durable_jobs SET email_state='FAILED',email_code='SMTP_IP_NOT_AUTHORIZED',status='NEEDS_ATTENTION' WHERE email_recipient=?",unique+"@mercato.invalid");
  mvc.perform(get("/api/auth/verification-delivery").cookie(owner.cookie())).andExpect(jsonPath("$.state").value("FAILED")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("couldn’t send")));
  var stranger=browser();
  mvc.perform(get("/api/auth/verification-delivery").cookie(stranger.cookie()).param("email",unique+"@mercato.invalid")).andExpect(jsonPath("$.state").value("UNKNOWN"));
  postJson(stranger,"/api/auth/resend-verification?email="+unique+"@mercato.invalid",Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("queued")));
  mvc.perform(get("/api/auth/verification-delivery").cookie(stranger.cookie())).andExpect(jsonPath("$.state").value("UNKNOWN"));
 }
 @Autowired org.springframework.context.ApplicationContext application;
 @Test void restApiCannotActivateTheVexExcludedViewRenderingFeatures(){
  assertTrue(application.getBeansOfType(org.springframework.web.servlet.view.xslt.XsltViewResolver.class).isEmpty());
  var mappings=application.getBeansOfType(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
  assertFalse(mappings.isEmpty());
  for(var mapping:mappings.values())for(var handler:mapping.getHandlerMethods().values()){
   if(!handler.getBeanType().getName().startsWith("com.mercato."))continue;
   assertNotNull(org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),org.springframework.web.bind.annotation.RestController.class),handler.toString());
   var result=handler.getReturnType().getParameterType();
   assertFalse(org.springframework.web.servlet.ModelAndView.class.isAssignableFrom(result),handler.toString());
   assertFalse(org.springframework.web.servlet.View.class.isAssignableFrom(result),handler.toString());
   assertFalse(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class.isAssignableFrom(result),handler.toString());
   assertFalse(result.getName().contains("FragmentsRendering"),handler.toString());
   String media=java.util.Arrays.toString(handler.getMethod().getAnnotations());
   assertFalse(media.contains("text/event-stream"),handler.toString());
  }
 }
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired MfaCredentialRepository credentials;@Autowired UserRepository users;@Autowired com.mercato.Security.jwt.JwtUtils jwt;@Autowired com.mercato.Repository.PasswordResetTokenRepository resetTokens;@Autowired com.mercato.Repository.RoleRepository roles;@Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;@Autowired SessionRevocation revocation;@Autowired AbuseCounters abuse;@Autowired LoginFailureRepository failures;@Autowired EmailChallengeRepository emailChallenges;@Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 record Browser(Cookie cookie,String context,String csrf){}
 Browser browser()throws Exception {var r=mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andReturn();var s=r.getResponse().getCookie("mercato-demo-session");var c=json.readTree(r.getResponse().getContentAsString()).get("contextId").asText();var csrf=json.readTree(mvc.perform(get("/api/auth/csrf").cookie(s)).andReturn().getResponse().getContentAsString()).get("token").asText();return new Browser(s,c,csrf);}
 ResultActions postJson(Browser b,String path,Object body)throws Exception{return mvc.perform(post(path).cookie(b.cookie()).header("X-Account-Context",b.context()).header("X-CSRF-TOKEN",b.csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
 Browser update(Browser b,MvcResult r)throws Exception {var j=json.readTree(r.getResponse().getContentAsString());var cookie=r.getResponse().getCookie("mercato-demo-session");return new Browser(cookie==null?b.cookie():cookie,j.get("contextId").asText(),b.csrf());}
 @Test void customerSessionHasNoBrowserCredentialAndRejectsCsrfAndStaleContext()throws Exception {
  var b=browser();mvc.perform(post("/api/auth/signin").cookie(b.cookie()).header("X-Account-Context",b.context()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
  var r=postJson(b,"/api/auth/signin",Map.of("username","shopperreview","password","ShopOnly!2026")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.user.password").doesNotExist()).andReturn();
  postJson(b,"/api/auth/signout",Map.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_CONTEXT_CHANGED"));
  b=update(b,r);mvc.perform(get("/api/auth/user").cookie(b.cookie())).andExpect(status().isOk());
  postJson(b,"/api/auth/signout",Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
  mvc.perform(get("/api/account/profile").header("Authorization","Bearer old-auth-jwt")).andExpect(status().isUnauthorized());
 }
 @Test void administratorRequiresEstablishedTotpAndRejectsStepReplay()throws Exception {
  String id=users.findByUsername("reviewer").orElseThrow().getUserId();credentials.deleteById(id);
  var b=browser();var r=postJson(b,"/api/auth/signin",Map.of("username","reviewer","password","ReviewOnly!2026")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false)).andExpect(jsonPath("$.mfa.state").value("ENROLL_REQUIRED")).andReturn();b=update(b,r);
  mvc.perform(get("/api/admin/users").cookie(b.cookie())).andExpect(status().isUnauthorized());
  var enroll=postJson(b,"/api/auth/mfa/enroll",Map.of()).andExpect(status().isOk()).andReturn();String seed=json.readTree(enroll.getResponse().getContentAsString()).get("secret").asText();String code=Totp.code(seed,Instant.now().getEpochSecond()/30);
  var verified=postJson(b,"/api/auth/mfa/verify",Map.of("code",code)).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.recoveryCodes.length()").value(10)).andReturn();b=update(b,verified);
  postJson(b,"/api/auth/mfa/verify",Map.of("code",code)).andExpect(status().isUnauthorized());
  var stored=credentials.findById(id).orElseThrow();assertFalse(stored.getEncryptedSeed().contains(seed));assertEquals(10,stored.getRecoveryHashes().size());
 }
 @Test void signedLegacyGuestMigrationIsTypeCheckedAndCanOnlyBeClaimedOnce()throws Exception {
  var b=browser();String guest=jwt.generateGuestTokenValue();
  var r=postJson(b,"/api/auth/legacy-guest-cart",Map.of("guestToken",guest)).andExpect(status().isOk()).andReturn();b=update(b,r);
  postJson(b,"/api/auth/legacy-guest-cart",Map.of("guestToken",guest)).andExpect(status().isUnauthorized());
  String auth=jwt.generateJwtToken(com.mercato.Security.services.UserDetailsImpl.build(users.findByUsername("shopperreview").orElseThrow()));
  postJson(b,"/api/auth/legacy-guest-cart",Map.of("guestToken",auth)).andExpect(status().isUnauthorized());
 }
 @Test void resettingPasswordInvalidatesEveryIndexedSession()throws Exception {
  var user=new com.mercato.Entity.EcommUser();user.setUsername("sessreset"+UUID.randomUUID().toString().substring(0,8));user.setEmail(user.getUsername()+"@mercato.invalid");user.setPassword(passwords.encode("OldPassword!2026"));user.setFirstName("Session");user.setLastName("Reset");user.setPhoneNumber(String.format(java.util.Locale.ROOT,"7%09d",new java.security.SecureRandom().nextInt(1000000000)));user.setEnabled(true);user.setEmailVerified(true);user.setRoles(new HashSet<>(Set.of(roles.findByRoleName(com.mercato.Entity.AppRole.ROLE_USER).orElseThrow())));users.saveAndFlush(user);
  var a=browser();a=update(a,postJson(a,"/api/auth/signin",Map.of("username",user.getUsername(),"password","OldPassword!2026")).andExpect(status().isOk()).andReturn());
  var b=browser();b=update(b,postJson(b,"/api/auth/signin",Map.of("username",user.getUsername(),"password","OldPassword!2026")).andExpect(status().isOk()).andReturn());
  var token=com.mercato.Entity.PasswordResetToken.builder().user(user).expiresAt(Instant.now().plusSeconds(600)).build();resetTokens.saveAndFlush(token);
  var reset=browser();postJson(reset,"/api/auth/reset-password",Map.of("token",token.getToken(),"password","NewPassword!2026")).andExpect(status().isOk());
  mvc.perform(get("/api/auth/session").cookie(a.cookie())).andExpect(jsonPath("$.authenticated").value(false));
  mvc.perform(get("/api/auth/session").cookie(b.cookie())).andExpect(jsonPath("$.authenticated").value(false));
  assertEquals(1,users.findByUserId(user.getUserId()).orElseThrow().getAccessVersion());
  postJson(reset,"/api/auth/reset-password",Map.of("token",token.getToken(),"password","AnotherPassword!2026")).andExpect(status().isBadRequest());
 }
 @Test void fiftyRollingAccountFailuresRequireSingleUseEmailProofAcrossClientAddresses()throws Exception {
  String name="shopperreview";String id=users.findByUsername(name).orElseThrow().getUserId();
  try {
   for(int i=0;i<50;i++){var f=new LoginFailure();f.setId(UUID.randomUUID().toString());f.setAccountHash(AuthHash.digest(name));f.setFailedAt(Instant.now());failures.save(f);}
   var b=browser();var signed=postJson(b,"/api/auth/signin",Map.of("username",name,"password","ShopOnly!2026")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false)).andExpect(jsonPath("$.mfa.state").value("STEP_UP_REQUIRED")).andReturn();b=update(b,signed);
   postJson(b,"/api/auth/step-up/send",Map.of()).andExpect(status().isOk());
   var challenge=emailChallenges.findById(id).orElseThrow();challenge.setCodeHash(AuthHash.digest(id+":123456"));emailChallenges.saveAndFlush(challenge);
   var verified=postJson(b,"/api/auth/step-up/verify",Map.of("code","123456")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andReturn();b=update(b,verified);
   assertTrue(emailChallenges.findById(id).isEmpty());postJson(b,"/api/auth/step-up/verify",Map.of("code","123456")).andExpect(status().isUnauthorized());
  } finally {failures.deleteAll();}
 }
 @Test void databaseBackoffUsesDeadlinesAndExpiresWithoutPermanentLock() {
  String key="budget-"+UUID.randomUUID();for(int i=0;i<5;i++)assertEquals(0,abuse.consume("test-client",key,5,3600,true));
  assertEquals(2,abuse.consume("test-client",key,5,3600,true));assertTrue(abuse.retryAfter("test-client",key)>0);
  jdbc.update("update auth_abuse_buckets set blocked_until=? where bucket_key=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),AuthHash.digest("test-client:"+key));
  assertEquals(4,abuse.consume("test-client",key,5,3600,true));
  jdbc.update("update auth_abuse_buckets set window_start=? where bucket_key=?",java.sql.Timestamp.from(Instant.now().minusSeconds(7200)),AuthHash.digest("test-client:"+key));
  assertEquals(0,abuse.consume("test-client",key,5,3600,true));
 }
}
