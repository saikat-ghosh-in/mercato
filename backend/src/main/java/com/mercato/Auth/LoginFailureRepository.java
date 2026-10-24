package com.mercato.Auth;
import org.springframework.data.jpa.repository.JpaRepository;import java.time.Instant;
public interface LoginFailureRepository extends JpaRepository<LoginFailure,String>{long countByAccountHashAndFailedAtAfter(String accountHash,Instant cutoff);long deleteByFailedAtBefore(Instant cutoff);}
