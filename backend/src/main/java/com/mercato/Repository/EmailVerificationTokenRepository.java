package com.mercato.Repository;

import com.mercato.Entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findByUserIdAndVerifiedAtIsNull(Long userId);

    void deleteByExpiresAtBefore(Instant now);
}
