package com.mercato.Repository;
import com.mercato.Entity.SellerPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SellerPromotionRepository extends JpaRepository<SellerPromotion,Long> {
    List<SellerPromotion> findBySellerIdOrderByCreatedAtDesc(String sellerId);
    Optional<SellerPromotion> findByRequestId(String requestId);
    List<SellerPromotion> findByStatus(String status);
}
