package com.mercato.Session;
import lombok.RequiredArgsConstructor;import org.springframework.session.FindByIndexNameSessionRepository;import org.springframework.session.Session;import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class SessionRevocation {
 private final FindByIndexNameSessionRepository<? extends Session> repository;
 public void revokeAll(String userId){repository.findByPrincipalName(userId).keySet().forEach(repository::deleteById);}
}
