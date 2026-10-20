package com.mercato.Repository;
import com.mercato.Entity.ListingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface ListingRequestRepository extends JpaRepository<ListingRequest,Long> {
    Optional<ListingRequest> findFirstByProductIdAndStatusOrderByIdDesc(String productId,String status);
    Page<ListingRequest> findBySellerId(String sellerId,Pageable page);
    Page<ListingRequest> findBySellerIdAndStatus(String sellerId,String status,Pageable page);
    long countBySellerId(String sellerId);
    long countBySellerIdAndStatus(String sellerId,String status);
}
