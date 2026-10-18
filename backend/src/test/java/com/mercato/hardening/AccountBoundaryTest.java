package com.mercato.hardening;
import com.mercato.Configuration.DataInitializer;
import com.mercato.Entity.EcommUser;
import com.mercato.ExceptionHandler.CustomConflictException;
import com.mercato.Payloads.Request.UpdateProfileRequestDTO;
import com.mercato.Repository.*;
import com.mercato.Service.*;
import com.mercato.Utils.AuthUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountBoundaryTest {
    @Test void demoPasswordCannotBootstrapOutsideDemoOrWithProductionEnabled() {
        for (String[] profiles : new String[][]{{"prod"}, {"local"}, {"demo","prod"}}) {
            var environment = new MockEnvironment(); environment.setActiveProfiles(profiles);
            var roleRepo = mock(RoleRepository.class); var userRepo = mock(UserRepository.class); var encoder = mock(PasswordEncoder.class);
            var initializer = new DataInitializer(roleRepo, userRepo, encoder, environment);
            ReflectionTestUtils.setField(initializer, "adminPassword", "ReviewOnly!2026");
            assertThrows(IllegalStateException.class, () -> initializer.run());
            verifyNoInteractions(roleRepo, userRepo, encoder);
        }
    }
    @Test void duplicatePhoneIsRejectedInsteadOfPretendingTheProfileWasSaved() {
        var users=mock(UserRepository.class); var auth=mock(AuthUtil.class);
        var service=new UserServiceImpl(users,mock(RoleRepository.class),auth,mock(EmailService.class));
        var user=EcommUser.builder().phoneNumber("9999999999").build();
        when(auth.getLoggedInUser()).thenReturn(user);
        when(users.existsByPhoneNumber("8888888888")).thenReturn(true);
        var request=new UpdateProfileRequestDTO(); request.setPhoneNumber("8888888888");
        assertThrows(CustomConflictException.class, () -> service.updateProfile(request));
        assertEquals("9999999999",user.getPhoneNumber()); verify(users,never()).save(any());
    }
    @Test void nullOrEmptyRoleUpdatesAreBadRequestsBeforeAnyDatabaseMutation() {
        var users=mock(UserRepository.class); var roles=mock(RoleRepository.class);
        var service=new UserServiceImpl(users,roles,mock(AuthUtil.class),mock(EmailService.class));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserRoles("USR-1",null));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserRoles("USR-1",Set.of()));
        assertThrows(IllegalArgumentException.class, () -> service.updateUserRoles("USR-1",new HashSet<>(Arrays.asList((String)null))));
        verifyNoInteractions(users,roles);
    }
}
