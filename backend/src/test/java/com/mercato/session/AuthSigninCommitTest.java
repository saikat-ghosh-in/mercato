package com.mercato.session;

import com.mercato.Auth.*;
import com.mercato.Entity.*;
import com.mercato.Jobs.DurableJobs;
import com.mercato.Repository.*;
import com.mercato.Security.jwt.JwtUtils;
import com.mercato.Security.payloads.LoginRequest;
import com.mercato.Security.services.AuthServiceImpl;
import com.mercato.Service.*;
import com.mercato.Session.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSigninCommitTest {
    @Test void failedDatabaseCommitCannotCreateAnAuthenticatedCookieSession() {
        var users=mock(UserRepository.class);
        var sessions=new SessionService(users,mock(JwtUtils.class));
        var transactions=mock(PlatformTransactionManager.class);
        var passwords=mock(PasswordEncoder.class);
        var environment=new MockEnvironment();environment.setActiveProfiles("demo");
        var service=new AuthServiceImpl(sessions,mock(MfaService.class),mock(AbuseCounters.class),
                environment,mock(DurableJobs.class),transactions,mock(SessionRevocation.class),users,
                mock(RoleRepository.class),passwords,mock(EmailVerificationService.class),mock(SellerOnboardingService.class));
        service.setCartService(mock(CartService.class));
        var user=new EcommUser();user.setUserId("USR-1");user.setUsername("shopper");user.setPassword("stored-hash");
        user.setEnabled(true);user.setEmailVerified(true);user.setRoles(Set.of(new Role(AppRole.ROLE_USER)));
        when(users.findByUsernameForUpdate("shopper")).thenReturn(Optional.of(user));
        when(users.findLoginUsernames("shopper")).thenReturn(List.of("shopper"));
        when(passwords.matches("password","stored-hash")).thenReturn(true);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("commit unavailable"))
                .when(transactions).commit(org.mockito.ArgumentMatchers.nullable(TransactionStatus.class));
        var request=new MockHttpServletRequest();var login=new LoginRequest();login.setUsername("shopper");login.setPassword("password");
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                ()->service.authenticateUser(login,request,new MockHttpServletResponse()));
        assertNull(sessions.identity(request));
    }
}
