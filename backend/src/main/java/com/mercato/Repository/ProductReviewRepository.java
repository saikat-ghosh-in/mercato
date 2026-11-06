package com.mercato.Repository;

import com.mercato.Entity.ProductReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    boolean existsByProductIdAndBuyerId(String productId, String buyerId);

    @Query("select r.productId from ProductReview r where r.buyerId = :buyerId and r.productId in :productIds")
    List<String> findReviewedProducts(String buyerId, Collection<String> productIds);

    Slice<ProductReview> findByProductIdOrderByCreatedAtDescIdDesc(String productId, Pageable pageable);

    @Query("select count(r) as count, avg(r.rating) as average from ProductReview r where r.productId = :productId")
    RatingSummary summarize(String productId);

    interface RatingSummary {
        long getCount();
        Double getAverage();
    }
}
