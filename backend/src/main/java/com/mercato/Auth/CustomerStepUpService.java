package com.mercato.Auth;

import com.mercato.Entity.EcommUser;
import com.mercato.Jobs.DurableJobs;
import com.mercato.Repository.UserRepository;
import com.mercato.Service.CartService;
import com.mercato.Session.SessionIdentity;
import com.mercato.Session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerStepUpService {
    private final UserRepository users;
    private final EmailChallengeRepository challenges;
    private final SessionService sessions;
    private final DurableJobs jobs;
    private final CartService carts;
    private final PlatformTransactionManager transactions;

    private EcommUser lockedUser(SessionIdentity id) {
        if (id == null || !"STEP_UP_REQUIRED".equals(id.mfaState())
                || id.authenticatedAt().plusSeconds(600).isBefore(Instant.now())) throw new AuthProofException();
        var user = users.findByUserIdForUpdate(id.userId()).orElseThrow(AuthProofException::new);
        if (user.isAdmin() || !user.isEnabled() || !user.isEmailVerified() || user.isAccountLocked()
                || user.getAccessVersion() != id.accessVersion()) throw new AuthProofException();
        return user;
    }

    @Transactional
    public void send(HttpServletRequest request) {
        var id = sessions.identity(request);
        var user = lockedUser(id);
        String code = String.format(java.util.Locale.ROOT, "%06d", new java.security.SecureRandom().nextInt(1000000));
        var challenge = new EmailChallenge();
        challenge.setUserId(id.userId());
        challenge.setCodeHash(AuthHash.digest(id.userId() + ":" + code));
        challenge.setSessionHash(AuthHash.digest(request.getSession().getId()));
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        challenges.save(challenge);
        jobs.enqueue("AUTH_EMAIL", "step-up:" + UUID.randomUUID(), Map.of("to", user.getEmail(),
                "subject", "Mercato sign-in verification", "text", "Your Mercato verification code is "
                        + code + ". It expires in 5 minutes."));
    }

    public Map<String, Object> verify(HttpServletRequest request, String code) {
        var id = sessions.identity(request);
        Boolean verified = new TransactionTemplate(transactions).execute(status -> {
            lockedUser(id);
            var challenge = challenges.findById(id.userId()).orElseThrow(AuthProofException::new);
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() > 5 || !challenge.getExpiresAt().isAfter(Instant.now())
                    || !challenge.getSessionHash().equals(AuthHash.digest(request.getSession().getId()))
                    || code == null || !challenge.getCodeHash().equals(AuthHash.digest(id.userId() + ":" + code))) {
                // Return, then throw after commit: failed attempts must survive the error response.
                return false;
            }
            String guest = (String) request.getSession().getAttribute(SessionService.GUEST);
            if (guest != null) carts.mergeGuestCartOnLogin(id.userId(), guest);
            challenges.delete(challenge);
            return true;
        });
        if (!Boolean.TRUE.equals(verified)) throw new AuthProofException();
        request.getSession().removeAttribute(SessionService.GUEST);
        sessions.completeMfa(request);
        return sessions.bootstrap(request);
    }
}
