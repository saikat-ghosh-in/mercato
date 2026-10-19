package com.mercato.Repository;
import com.mercato.Entity.SellerNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface SellerNoticeRepository extends JpaRepository<SellerNotice, Long> {
    List<SellerNotice> findByUserIdOrderByCreatedAtDesc(String userId);
    List<SellerNotice> findByChange_IdAndEventType(Long id, String eventType);
    List<SellerNotice> findByEmailSentAtIsNull();
    List<SellerNotice> findTop20ByEmailSentAtIsNullAndSupersededFalseAndNextAttemptAtLessThanEqualOrderByIdAsc(java.time.Instant now);
    Optional<SellerNotice> findByIdAndUserId(Long id, String userId);
    boolean existsByUserIdAndChange_IdAndEventType(String userId, Long id, String type);
}
