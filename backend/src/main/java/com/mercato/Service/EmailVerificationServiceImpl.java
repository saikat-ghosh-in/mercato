package com.mercato.Service;

import com.mercato.Entity.EcommUser;
import com.mercato.Entity.EmailVerificationToken;
import com.mercato.ExceptionHandler.CustomBadRequestException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Repository.EmailVerificationTokenRepository;
import com.mercato.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final com.mercato.Jobs.DurableJobs jobs;
    @Value("${frontend.base.url}") private String frontendUrl;

    @Value("${mercato.email.verification.expiry-hours:24}")
    private int expiryHours;

    @Transactional
    @Override
    public void createAndSendVerificationToken(EcommUser user) {
        user=userRepository.findByUserIdForUpdate(user.getUserId()).orElseThrow();
        if(user.isEmailVerified())return;
        tokenRepository.findByUserIdAndVerifiedAtIsNull(user.getId())
                .ifPresent(tokenRepository::delete);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .expiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS))
                .build();

        tokenRepository.save(token);

        jobs.enqueue("AUTH_EMAIL","verify-email:"+token.getToken(),java.util.Map.of("to",user.getEmail(),"subject","Verify your Mercato email","text","Confirm your email: "+frontendUrl+"/verify-email?token="+token.getToken()));

        log.info("Verification token created for user: {}", user.getUserId());
    }

    @Transactional
    @Override
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new CustomBadRequestException("Invalid verification token"));

        if (verificationToken.isVerified()) {
            throw new CustomBadRequestException("Email already verified");
        }

        if (verificationToken.isExpired()) {
            throw new CustomBadRequestException("Verification token has expired. Please request a new one.");
        }

        verificationToken.setVerifiedAt(Instant.now());
        tokenRepository.save(verificationToken);

        EcommUser user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        jobs.enqueue("AUTH_EMAIL","welcome:"+user.getUserId(),java.util.Map.of("to",user.getEmail(),"subject","Welcome to Mercato","text","Your email is verified. You can now sign in and explore Mercato."));

        log.info("Email verified for user: {}", user.getUserId());
    }

    @Transactional
    @Override
    public void resendVerificationEmail(String email) {
        EcommUser user = userRepository.findByEmail(email).orElse(null);
        if(user==null)return;

        if (user.isEmailVerified()) {
            return;
        }

        createAndSendVerificationToken(user);
    }
}
