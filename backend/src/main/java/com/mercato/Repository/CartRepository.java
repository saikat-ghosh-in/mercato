package com.mercato.Repository;

import com.mercato.Entity.cart.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @Query("select c.id from Cart c where c.user.userId=:userId")
    Optional<Long> findIdByUserId(@org.springframework.data.repository.query.Param("userId") String userId);
    @Query("select c.id from Cart c where c.guestToken=:token")
    Optional<Long> findIdByGuestToken(@org.springframework.data.repository.query.Param("token") String token);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.id=:id")
    Optional<Cart> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    @Query("select i.product.productId from CartItem i where i.cart.id=:id order by i.product.productId")
    List<String> productIds(@org.springframework.data.repository.query.Param("id") Long id);

    @EntityGraph(attributePaths = {"cartItems", "cartItems.product"})
    Optional<Cart> findByUser_UserId(String userId);

    @EntityGraph(attributePaths = {"cartItems", "cartItems.product"})
    Optional<Cart> findByGuestToken(String guestToken);

    Optional<Cart> findByCartId(String cartId);

    @Modifying
    @Query("""
            DELETE FROM Cart c
            WHERE c.guestToken IS NOT NULL
            AND c.updatedAt < :cutoff
            """)
    void deleteStaleGuestCarts(Instant cutoff);

    @EntityGraph(attributePaths = {"cartItems", "cartItems.product"})
    @Query("""
            SELECT c FROM Cart c
            WHERE c.guestToken IS NOT NULL
            AND c.updatedAt < :cutoff
            """)
    List<Cart> findStaleGuestCarts(Instant cutoff);

    void deleteByUserId(Long userId);
}
