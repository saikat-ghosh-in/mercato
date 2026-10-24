package com.mercato.Auth;

import com.mercato.Entity.EcommUser;
import com.mercato.Repository.UserRepository;
import com.mercato.Service.CartService;
import com.mercato.Session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MfaService {
    private static final String PENDING_SEED = "mercato.mfa.pending-seed";
    private final MfaCredentialRepository credentials;
    private final UserRepository users;
    private final SeedCipher cipher;
    private final SessionService sessions;
    private final CartService carts;
    private final org.springframework.transaction.PlatformTransactionManager transactions;
    private final com.mercato.Session.SessionRevocation revocation;
    private final com.mercato.Repository.AccountAccessEventRepository audit;

    public boolean enrolled(String userId) {
        return credentials.findById(userId).map(c -> c.getEncryptedSeed() != null).orElse(false);
    }

    private EcommUser lockedUser(HttpServletRequest request) {
        var id = sessions.identity(request);
        if (id == null || !id.expiresAt().isAfter(Instant.now())
                || (!id.privileged() && id.authenticatedAt().plusSeconds(600).isBefore(Instant.now()))) {
            throw new AuthProofException();
        }
        var user = users.findByUserIdForUpdate(id.userId()).orElseThrow(AuthProofException::new);
        if (!user.isEnabled() || user.isAccountLocked() || !user.isEmailVerified()
                || user.getAccessVersion() != id.accessVersion()) throw new AuthProofException();
        return user;
    }

    @Transactional
    public Map<String, String> enroll(HttpServletRequest request, String proof, boolean replacement) {
        var user = lockedUser(request);
        if (!user.isAdmin()) throw new AuthProofException();
        var credential = credentials.findById(user.getUserId()).orElseGet(() -> {
            var c = new MfaCredential();
            c.setUserId(user.getUserId());
            return c;
        });
        if (credential.getEncryptedSeed() != null) {
            if (!replacement || !sessions.identity(request).privileged()
                    || (!sessions.recentMfa(request) && !verifyCode(credential, proof))) {
                throw new AuthProofException();
            }
        } else if (replacement) throw new AuthProofException();

        String secret = Totp.secret();
        credential.setPendingSeed(cipher.encrypt(user.getUserId(), secret));
        credential.setPendingExpiresAt(Instant.now().plusSeconds(600));
        credentials.save(credential);
        // Bind confirmation to the session that received the new seed. Other sessions can
        // continue to prove their established authenticator during an unfinished replacement.
        request.getSession().setAttribute(PENDING_SEED, credential.getPendingSeed());
        String label = java.net.URLEncoder.encode("Mercato:" + user.getUsername(), StandardCharsets.UTF_8);
        return Map.of("secret", secret, "otpauthUri", "otpauth://totp/" + label
                + "?secret=" + secret + "&issuer=Mercato&algorithm=SHA1&digits=6&period=30");
    }

    @Transactional
    public void operatorRecovery(HttpServletRequest request, String targetId, String reason, Long expectedVersion) {
        var actor=sessions.currentUser(request);
        var identity=sessions.identity(request);
        if(actor==null || !actor.isAdmin() || identity==null || !identity.privileged()
                || !sessions.recentMfa(request) || actor.getUserId().equals(targetId)) {
            throw new org.springframework.security.access.AccessDeniedException("Another administrator with recent MFA proof is required");
        }
        if(reason==null || reason.strip().length()<20 || reason.strip().length()>500 || expectedVersion==null)
            throw new IllegalArgumentException("Provide the current account version and a recovery reason between 20 and 500 characters");
        var target=users.findByUserIdForUpdate(targetId).orElseThrow(AuthProofException::new);
        if(!target.isAdmin()) throw new IllegalArgumentException("Operator authenticator recovery is restricted to administrator accounts");
        if(target.getAccessVersion()!=expectedVersion) throw new com.mercato.ExceptionHandler.CustomConflictException("The account changed. Refresh before recovering its authenticator");
        var credential=credentials.findById(targetId).orElseThrow(AuthProofException::new);
        credentials.delete(credential);
        target.setAccessVersion(target.getAccessVersion()+1);
        users.save(target);
        revocation.revokeAll(targetId);
        audit.save(new com.mercato.Entity.AccountAccessEvent(target,actor,"MFA_OPERATOR_RECOVERY",reason.strip(),
                "Established administrator authenticator", "Operator recovery approved; enrollment required"));
    }
    private boolean verifyCode(MfaCredential credential, String code) {
        long step = Totp.match(cipher.decrypt(credential.getUserId(), credential.getEncryptedSeed()), code, Instant.now());
        if (step < 0 || step <= credential.getLastAcceptedStep()) return false;
        credential.setLastAcceptedStep(step);
        return true;
    }

    public Map<String, Object> verify(HttpServletRequest request, String code, boolean recovery) {
        var proof = new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> consumeProof(request, code, recovery));
        request.getSession().removeAttribute(PENDING_SEED);
        sessions.completeMfa(request);
        if(proof.guestMerged()) request.getSession().removeAttribute(SessionService.GUEST);
        Map<String,Object> result=sessions.bootstrap(request);
        if(proof.codes()!=null) result.put("recoveryCodes",proof.codes());
        return result;
    }

    private record ConsumedProof(List<String> codes, boolean guestMerged) {}
    private ConsumedProof consumeProof(HttpServletRequest request,String code,boolean recovery) {
        var user = lockedUser(request);
        var c = credentials.findById(user.getUserId()).orElseThrow(AuthProofException::new);
        List<String> codes = null;
        boolean confirmsPending = c.getPendingSeed() != null
                && c.getPendingSeed().equals(request.getSession().getAttribute(PENDING_SEED))
                && c.getPendingExpiresAt() != null && c.getPendingExpiresAt().isAfter(Instant.now());
        if (recovery) {
            if (c.getEncryptedSeed() == null || code == null || !c.getRecoveryHashes().remove(
                    AuthHash.digest(user.getUserId() + ":" + code.strip().toUpperCase(Locale.ROOT)))) {
                throw new AuthProofException();
            }
        } else if (confirmsPending) {
            long step = Totp.match(cipher.decrypt(user.getUserId(), c.getPendingSeed()), code, Instant.now());
            if (step < 0) throw new AuthProofException();
            c.setEncryptedSeed(c.getPendingSeed());
            c.setPendingSeed(null);
            c.setPendingExpiresAt(null);
            c.setLastAcceptedStep(step);
            c.setEnrolledAt(Instant.now());
            c.getRecoveryHashes().clear();
            codes = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String value = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
                codes.add(value);
                c.getRecoveryHashes().add(AuthHash.digest(user.getUserId() + ":" + value));
            }
        } else if (c.getEncryptedSeed() == null || !verifyCode(c, code)) throw new AuthProofException();

        credentials.save(c);
        String guest = (String) request.getSession().getAttribute(SessionService.GUEST);
        if (guest != null) carts.mergeGuestCartOnLogin(user.getUserId(), guest);
        return new ConsumedProof(codes,guest!=null);
    }
}
