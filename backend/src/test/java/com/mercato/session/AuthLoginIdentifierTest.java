package com.mercato.session;

import com.mercato.Auth.*;
import com.mercato.Entity.*;
import com.mercato.Jobs.DurableJobs;
import com.mercato.Repository.*;
import com.mercato.Security.payloads.LoginRequest;
import com.mercato.Security.services.AuthServiceImpl;
import com.mercato.Service.*;
import com.mercato.Session.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthLoginIdentifierTest {
    final UserRepository users=mock(UserRepository.class);
    final SessionService sessions=mock(SessionService.class);
    final AbuseCounters abuse=mock(AbuseCounters.class);
    final MfaService mfa=mock(MfaService.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class);
    final EcommUser user=new EcommUser();
    final AuthServiceImpl service=new AuthServiceImpl(sessions,mfa,abuse,new MockEnvironment(),mock(DurableJobs.class),mock(PlatformTransactionManager.class),mock(SessionRevocation.class),users,mock(RoleRepository.class),passwords,mock(EmailVerificationService.class),mock(SellerOnboardingService.class));
    @BeforeEach void setup(){
        user.setUserId("USER-1");user.setUsername("shopper");user.setEmail("shopper@example.test");user.setPassword("stored-hash");user.setEmailVerified(true);user.setEnabled(true);user.setRoles(Set.of(new Role(AppRole.ROLE_USER)));
        when(users.findByUsernameForUpdate("shopper")).thenReturn(Optional.of(user));when(passwords.matches("correct","stored-hash")).thenReturn(true);
        service.setCartService(mock(CartService.class));
    }
    LoginRequest login(String id,String password){var r=new LoginRequest();r.setUsername(id);r.setPassword(password);return r;}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"shopper","shopper@example.test","SHOPPER@EXAMPLE.TEST"," shopper@example.test "})
    void usernameAndEmailUseOneAccountAndTheSameAbuseCounters(String identifier){
        // Project the same canonical username that the repository resolves from either identifier.
        doReturn(List.of("shopper")).when(users).findLoginUsernames(identifier.trim());
        var request=new MockHttpServletRequest();service.authenticateUser(login(identifier,"correct"),request,new MockHttpServletResponse());
        verify(sessions).establish(request,user,"NONE");verify(abuse).requiresStepUp("shopper");verify(abuse).retryAfter("login-account-delay","shopper");
    }
    @Test void emailLoginStillRequiresAdminMfa(){
        doReturn(List.of("shopper")).when(users).findLoginUsernames(user.getEmail());user.setRoles(Set.of(new Role(AppRole.ROLE_ADMIN)));when(mfa.enrolled("USER-1")).thenReturn(true);
        var request=new MockHttpServletRequest();service.authenticateUser(login(user.getEmail(),"correct"),request,new MockHttpServletResponse());verify(sessions).establish(request,user,"CHALLENGE_REQUIRED");
    }
    @Test void wrongEmailPasswordIsGenericAndCountsAgainstTheCanonicalAccount(){
        doReturn(List.of("shopper")).when(users).findLoginUsernames(user.getEmail());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->service.authenticateUser(login(user.getEmail(),"wrong"),new MockHttpServletRequest(),new MockHttpServletResponse()));
        verify(abuse).failed("shopper");verify(sessions,never()).establish(any(),any(),anyString());
    }
    @Test void ambiguousIdentifierCannotAuthenticateTheWrongAccount(){
        when(users.findLoginUsernames("shared@example.test")).thenReturn(List.of("shopper","shared@example.test"));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->service.authenticateUser(login("shared@example.test","correct"),new MockHttpServletRequest(),new MockHttpServletResponse()));
        verify(users,never()).findByUsernameForUpdate(anyString());verify(sessions,never()).establish(any(),any(),anyString());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"username","email"})
    void registrationCannotShadowAnotherAccountsLoginIdentifier(String field){
        var registration=new com.mercato.Security.payloads.RegisterUserRequest();registration.setUsername("new-user");registration.setEmail("new@example.test");
        when(users.existsByEmailIgnoreCase(field.equals("username")?"new-user":"new@example.test")).thenReturn(true);
        assertThrows(com.mercato.ExceptionHandler.ResourceAlreadyExistsException.class,()->service.registerNewUser(registration));
        verify(users,never()).save(any());
    }
    @Test void newEmailCannotShadowAnExistingEmailShapedUsername(){
        var registration=new com.mercato.Security.payloads.RegisterUserRequest();registration.setUsername("new-user");registration.setEmail("old@example.test");
        when(users.existsByUsernameIgnoreCase("old@example.test")).thenReturn(true);
        assertThrows(com.mercato.ExceptionHandler.ResourceAlreadyExistsException.class,()->service.registerNewUser(registration));verify(users,never()).save(any());
    }
}
