package com.mercato.Repository;

import com.mercato.Entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Query("select p.productId,p.primaryImageUrl from Product p where p.productId in :ids")
    List<Object[]> primaryImages(@Param("ids") java.util.Collection<String> ids);

    Optional<Product> findByProductIdAndActiveTrue(String productId);

    List<Product> findAllBySellerUserId(String sellerId, Sort sort);
    org.springframework.data.domain.Page<Product> findBySellerUserId(String sellerId, org.springframework.data.domain.Pageable page);
    long countBySellerUserId(String sellerId);

    Optional<Product> findByProductIdAndSellerUserId(String productId, String sellerId);

    @Query("SELECT p FROM Product p WHERE p.productId = :productId")
    Optional<Product> findByProductId(@Param("productId") String productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.productId = :productId")
    Optional<Product> findByProductIdForUpdate(@Param("productId") String productId);

    boolean existsByProductNameAndSellerUserId(String productName, String sellerId);

    boolean existsByCategoryCategoryId(String categoryId);

    @Query("""
            SELECT p FROM Product p
            WHERE p.seller.email = :sellerEmail
            AND p.active = true
            AND (p.physicalQty - p.reservedQty) <= :threshold
            ORDER BY (p.physicalQty - p.reservedQty) ASC
            """)
    List<Product> findLowStockProductsBySeller(@Param("sellerEmail") String sellerEmail,
                                               @Param("threshold") int threshold);
}
