package com.mercato.Repository;

import com.mercato.Entity.fulfillment.OrderLine;
import com.mercato.Entity.fulfillment.OrderLineStatus;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {
    // Conservative funding eligibility: no refunded orders, unknown historic rates or unpaid sales.
    @Query("""
        select coalesce(sum(l.unitPrice * l.shippedQty -
          case when l.commissionCapPerItem is null then round(l.unitPrice * l.shippedQty * l.commissionRatePercent / 100, 2)
          else least(round(l.unitPrice * l.commissionRatePercent / 100, 2), l.commissionCapPerItem) * l.shippedQty end
          - coalesce(round(l.sellerDeliveryCharge * l.shippedQty / l.orderedQty, 2),0)),0)
        from OrderLine l where l.sellerEmail = :email and l.commissionRatePercent is not null
          and l.order.paymentStatus = com.mercato.Entity.fulfillment.payment.PaymentStatus.SUCCESS
          and l.order.inventoryFinalizationFailed = false and l.order.payment.refunds is empty and l.order.automaticFullRefund = false
        """)
    BigDecimal eligiblePromotionEarnings(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ol FROM OrderLine ol
            JOIN FETCH ol.order
            WHERE ol.fulfillmentId = :fulfillmentId
            AND ol.orderLineNumber = :orderLineNumber
            """)
    Optional<OrderLine> findByFulfillmentIdAndOrderLineNumberForUpdate(
            @Param("fulfillmentId") String fulfillmentId,
            @Param("orderLineNumber") int orderLineNumber
    );

    @EntityGraph(attributePaths = {"order", "stateTransitions"})
    @Query("select l from OrderLine l join l.order o where l.sellerEmail=:sellerEmail and " + com.mercato.Service.SellerOrderVisibility.ORDER_PREDICATE)
    List<OrderLine> findAllBySellerEmail(@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);

    @EntityGraph(attributePaths = {"order", "stateTransitions"})
    @Query("select l from OrderLine l join l.order o where l.fulfillmentId=:fulfillmentId and l.sellerEmail=:sellerEmail and " + com.mercato.Service.SellerOrderVisibility.ORDER_PREDICATE)
    List<OrderLine> findAllByFulfillmentIdAndSellerEmail(@Param("fulfillmentId") String fulfillmentId,@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);

    @Query("""
            SELECT COALESCE(SUM(ol.revenue), 0)
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            """)
    BigDecimal findRevenueBySeller(@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);

    @Query("""
            SELECT COALESCE(SUM(ol.revenue), 0)
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            AND ol.updatedAt >= :start AND ol.updatedAt < :end
            """)
    BigDecimal findRevenueBySellerBetween(@Param("sellerEmail") String sellerEmail,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end,@Param("visibleAt") Instant visibleAt);

    @Query("""
            SELECT COALESCE(AVG(fulfillmentRevenue), 0)
            FROM (
                SELECT SUM(ol.revenue) AS fulfillmentRevenue
                FROM OrderLine ol
                WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
                GROUP BY ol.fulfillmentId
            )
            """)
    BigDecimal findAverageRevenuePerOrderBySeller(@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);

    @Query("""
            SELECT COUNT(DISTINCT ol.fulfillmentId)
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            """)
    long countTotalOrdersBySeller(@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);

    @Query("""
            SELECT COUNT(DISTINCT ol.fulfillmentId)
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            AND ol.createdAt BETWEEN :start AND :end
            """)
    long countOrdersBySellerCreatedBetween(
            @Param("sellerEmail") String sellerEmail,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("visibleAt") Instant visibleAt
    );

    @Query("""
            SELECT COUNT(DISTINCT ol.fulfillmentId)
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            AND ol.order.paymentStatus IN :statuses
            """)
    long countPendingPaymentBySeller(
            @Param("sellerEmail") String sellerEmail,
            @Param("visibleAt") Instant visibleAt,
            @Param("statuses") List<PaymentStatus> statuses
    );

    @Query("""
            SELECT COUNT(DISTINCT ol.fulfillmentId)
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            AND ol.orderLineStatus IN :statuses
            """)
    long countActiveOrdersBySeller(
            @Param("sellerEmail") String sellerEmail,
            @Param("visibleAt") Instant visibleAt,
            @Param("statuses") List<OrderLineStatus> statuses
    );

    @Query("""
            SELECT COALESCE(AVG(fulfillmentTotal), 0)
            FROM (
                SELECT SUM(ol.lineTotal) AS fulfillmentTotal
                FROM OrderLine ol
                WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
                GROUP BY ol.fulfillmentId
            )
            """)
    BigDecimal findAverageOrderValueBySeller(@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);

    @Query("""
            SELECT ol.productId, ol.productName,
                   SUM(ol.shippedQty) AS totalShippedQty,
                   SUM(ol.unitPrice * ol.shippedQty) AS totalRevenue
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            AND ol.shippedQty > 0
            GROUP BY ol.productId, ol.productName
            ORDER BY totalShippedQty DESC
            """)
    List<Object[]> findTopSellingProductsBySeller(@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);

    @Query("""
            SELECT ol.productId, ol.productName,
                   SUM(ol.shippedQty) AS totalShippedQty,
                   SUM(ol.unitPrice * ol.shippedQty) AS totalRevenue
            FROM OrderLine ol
            WHERE ol.shippedQty > 0
            GROUP BY ol.productId, ol.productName
            ORDER BY totalShippedQty DESC
            """)
    List<Object[]> findTopSellingProducts();

    @Query("""
            SELECT ol.orderLineStatus, COUNT(ol)
            FROM OrderLine ol
            WHERE ol.sellerEmail = :sellerEmail
            AND ol.order.confirmedAt IS NOT NULL AND ol.order.sellerActionsAvailableAt IS NOT NULL
            AND ol.order.sellerActionsAvailableAt <= :visibleAt
            AND (ol.order.workflowPolicyAdoptedAt IS NULL OR ol.order.workflowPolicyAdoptedAt <= :visibleAt)
            GROUP BY ol.orderLineStatus
            """)
    List<Object[]> findOrderLineStatusBreakdownBySeller(@Param("sellerEmail") String sellerEmail,@Param("visibleAt") Instant visibleAt);
}
