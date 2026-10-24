package com.mercato.session;
import com.mercato.Session.SessionIdentity;
import com.mercato.Session.SessionService;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Role;
import com.mercato.Entity.AppRole;
import com.mercato.Repository.UserRepository;
import com.mercato.Security.jwt.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SessionLifecycleTest {
    @Test void signInRotatesSessionAndNeverStoresPasswordInPrincipal() {
        var users=mock(UserRepository.class); var jwt=mock(JwtUtils.class);
        var service=new SessionService(users,jwt);
        var user=new EcommUser(); user.setUserId("USR-1"); user.setUsername("alice"); user.setEnabled(true); user.setEmailVerified(true); user.setPassword("secret"); user.setRoles(Set.of(new Role(AppRole.ROLE_USER)));
        var request=new MockHttpServletRequest(); request.getSession(); String old=request.getSession().getId();
        service.establish(request,user,"NONE");
        assertNotEquals(old,request.getSession().getId());
        var identity=(SessionIdentity)request.getSession().getAttribute(SessionService.IDENTITY);
        assertEquals("USR-1",identity.userId());
        assertEquals(604800,identity.expiresAt().getEpochSecond()-identity.authenticatedAt().getEpochSecond());
        assertNull(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT"));
    }
    @Test void accessVersionChangeRevokesSessionBeforeIdentityCanBeUsed() {
        var users=mock(UserRepository.class); var service=new SessionService(users,mock(JwtUtils.class));
        var user=new EcommUser(); user.setUserId("USR-1"); user.setAccessVersion(2); user.setEnabled(true); user.setEmailVerified(true); user.setRoles(Set.of());
        when(users.findByUserId("USR-1")).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest(); request.getSession().setAttribute(SessionService.IDENTITY,new SessionIdentity("USR-1",1,Instant.now(),Instant.now().plusSeconds(600),"NONE"));
        assertNull(service.currentUser(request));
        assertNull(request.getSession(false));
    }
    @Test void successfulMfaDoesNotExtendOriginalAbsoluteAuthenticationWindow() {
        var service=new SessionService(mock(UserRepository.class),mock(JwtUtils.class));
        var request=new MockHttpServletRequest();
        var original=new SessionIdentity("USR-1",0,Instant.now().minusSeconds(600),Instant.now().plusSeconds(600),"CHALLENGE_REQUIRED");
        request.getSession().setAttribute(SessionService.IDENTITY,original);
        service.completeMfa(request);
        var completed=service.identity(request);
        assertEquals(original.authenticatedAt(),completed.authenticatedAt());
        assertEquals(original.expiresAt(),completed.expiresAt());
        assertEquals("VERIFIED",completed.mfaState());
    }
}
