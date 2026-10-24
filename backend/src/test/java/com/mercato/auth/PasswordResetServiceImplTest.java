package com.mercato.auth;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.PasswordResetToken;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.Repository.PasswordResetTokenRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Service.EmailService;
import com.mercato.Service.PasswordResetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.mercato.Session.SessionRevocation sessions;
    @Mock private com.mercato.Jobs.DurableJobs jobs;
    @Mock private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private EcommUser user;
    private PasswordResetToken token;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "expiryHours", 1);
        ReflectionTestUtils.setField(passwordResetService, "entityManager", entityManager);
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "http://localhost:5173");

        user = new EcommUser();
        user.setId(1L);
        user.setUserId("USR-1");
        user.setEmail("user@example.com");
        user.setFirstName("Test");
        user.setEnabled(true);
        user.setPassword("old-encoded");

        token = PasswordResetToken.builder()
                .token("reset-token")
                .user(user)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
    }

    @Test
    void resetPassword_updatesPasswordAndMarksTokenUsed() {
        when(tokenRepository.findByToken("reset-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        passwordResetService.resetPassword("reset-token", "new-password");

        verify(userRepository).save(user);
        assertTrue(token.isUsed());
        org.junit.jupiter.api.Assertions.assertEquals(1,user.getAccessVersion());
        verify(sessions).revokeAll("USR-1");
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPassword_rejectsExpiredToken() {
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepository.findByToken("reset-token")).thenReturn(Optional.of(token));

        assertThrows(CustomBadRequestException.class,
                () -> passwordResetService.resetPassword("reset-token", "new-password"));
    }

    @Test
    void requestPasswordReset_sendsEmailForEnabledUser() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset("user@example.com");

        verify(tokenRepository).deleteByUser_Id(1L);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(jobs).enqueue(eq("AUTH_EMAIL"), org.mockito.ArgumentMatchers.startsWith("password-reset:"), org.mockito.ArgumentMatchers.argThat(payload -> "user@example.com".equals(payload.get("to")) && payload.get("text").toString().contains("/reset-password?token=")));
        org.mockito.Mockito.verifyNoInteractions(emailService);
    }
}
