package com.mercato.Commerce;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GatewayAttemptRepository extends JpaRepository<GatewayAttempt,String> {
    long countByOrderIdAndAuthorizationId(String orderId,String authorizationId);
}
