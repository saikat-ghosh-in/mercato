package com.mercato.Auth;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MfaCredentialRepository extends JpaRepository<MfaCredential,String> {}
