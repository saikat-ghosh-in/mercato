package com.mercato.Repository;

import com.mercato.Entity.cart.CartReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CartReservationRepository extends JpaRepository<CartReservation, Long> {

    Optional<CartReservation> findByCartItem_CartItemId(Long cartItemId);

    interface ExpiredHold {
        Long getId();
        String getProductId();
    }

    // Candidate identifiers only: loading entities here would retain stale expiry
    // values while cleanup waits for a concurrent checkout's product lock.
    @Query("""
            SELECT r.id AS id, r.product.productId AS productId FROM CartReservation r
            WHERE r.expiresAt <= :now
            ORDER BY r.product.productId, r.id
            """)
    List<ExpiredHold> findExpiredCandidates(Instant now);

    @Query("""
            SELECT r FROM CartReservation r
            LEFT JOIN FETCH r.product
            WHERE r.cartItem.cartItemId IN :cartItemIds
            """)
    List<CartReservation> findAllByCartItemIds(List<Long> cartItemIds);

    @Query("""
            SELECT cr FROM CartReservation cr
            LEFT JOIN FETCH cr.cartItem
            WHERE cr.product.productId = :productId
            ORDER BY cr.createdAt DESC
            """)
    List<CartReservation> findAllByProductIdOrderByCreatedAtDesc(@Param("productId") String productId);
}
