package com.mercato.Repository;

import com.mercato.Entity.fulfillment.Order;
import com.mercato.Entity.fulfillment.OrderStatus;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import com.mercato.Payloads.Response.AdminOrderSummaryDTO;
import com.mercato.Payloads.Response.OrderSummaryDTO;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    long countByCustomerEmail(String email);
    @Query("select new com.mercato.Payloads.Response.OrderSummaryDTO(o.orderId,o.orderStatus,o.paymentStatus,o.totalAmount,o.createdAt) from Order o where o.customerEmail=:email")
    org.springframework.data.domain.Page<OrderSummaryDTO> userActivityOrders(@Param("email") String email,org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths={"orderLines", "payment"})
    @Query("select distinct o from Order o where o.createdAt >= :start and o.createdAt < :end and (:email is null or exists (select l.id from OrderLine l where l.order = o and l.sellerEmail = :email)) order by o.createdAt desc")
    List<Order> financialOrders(@Param("start") Instant start, @Param("end") Instant end, @Param("email") String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId=:orderId")
    Optional<Order> lockByOrderId(@Param("orderId") String orderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=(select l.order.id from OrderLine l where l.fulfillmentId=:fulfillmentId and l.orderLineNumber=:lineNumber)")
    Optional<Order> lockByFulfillment(@Param("fulfillmentId")String fulfillmentId,@Param("lineNumber")int lineNumber);

    @EntityGraph(attributePaths = {"orderLines"})
    Optional<Order> findByOrderId(String orderId);

    @EntityGraph(attributePaths = {"orderLines"})
    Optional<Order> findByOrderIdAndCustomerEmail(String orderId, String email);

    @EntityGraph(attributePaths = {"orderLines"})
    List<Order> findByCustomerEmail(String email);

    @Query("""
            SELECT new com.mercato.Payloads.Response.OrderSummaryDTO(
                o.orderId, o.orderStatus, o.paymentStatus, o.totalAmount, o.createdAt
            )
            FROM Order o
            WHERE o.customerEmail = :email
            ORDER BY o.createdAt DESC
            """)
    List<OrderSummaryDTO> findOrderSummariesByCustomerEmail(@Param("email") String email);

    @Query("""
            SELECT new com.mercato.Payloads.Response.AdminOrderSummaryDTO(
                o.orderId, o.customerName, o.customerEmail, o.orderStatus, o.totalAmount, o.createdAt
            )
            FROM Order o
            ORDER BY o.createdAt DESC
            """)
    List<AdminOrderSummaryDTO> findAllOrderSummaries();

    @Query("SELECT COUNT(o) FROM Order o")
    long countTotalOrders();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    long countOrdersCreatedBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderStatus IN :statuses")
    long countByOrderStatusIn(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus IN :statuses")
    long countByPaymentStatusIn(@Param("statuses") List<PaymentStatus> statuses);

    @Query("""
            SELECT COALESCE(AVG(o.totalAmount), 0)
            FROM Order o
            """)
    BigDecimal findAverageOrderValue();

    @Query(value = """
            SELECT CAST(COALESCE(SUM(p.captured_amount - COALESCE(r.returned_amount,0)),0) AS NUMERIC(15,2))
            FROM payments p LEFT JOIN (
                SELECT payment_fk,SUM(amount) AS returned_amount FROM refunds WHERE status='SUCCESS' GROUP BY payment_fk
            ) r ON r.payment_fk=p.id WHERE p.captured_at IS NOT NULL
            """, nativeQuery = true)
    BigDecimal findTotalRevenue();

    @Query(value = """
            SELECT CAST(COALESCE(SUM(p.captured_amount - COALESCE(r.returned_amount,0)),0) AS NUMERIC(15,2))
            FROM payments p LEFT JOIN (
                SELECT payment_fk,SUM(amount) AS returned_amount FROM refunds WHERE status='SUCCESS' GROUP BY payment_fk
            ) r ON r.payment_fk=p.id WHERE p.captured_at IS NOT NULL AND p.captured_at BETWEEN :start AND :end
            """, nativeQuery = true)
    BigDecimal findTotalRevenueBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            SELECT CAST(COALESCE(AVG(p.captured_amount - COALESCE(r.returned_amount,0)),0) AS NUMERIC(15,2))
            FROM payments p LEFT JOIN (
                SELECT payment_fk,SUM(amount) AS returned_amount FROM refunds WHERE status='SUCCESS' GROUP BY payment_fk
            ) r ON r.payment_fk=p.id WHERE p.captured_at IS NOT NULL
            """, nativeQuery = true)
    BigDecimal findAverageRevenuePerOrder();

    @Query("""
                SELECT o FROM Order o
                ORDER BY o.createdAt DESC
                LIMIT :count
            """)
    List<Order> findRecentOrders(@Param("count") int count);

    @Query("""
                SELECT o.orderStatus, COUNT(o) FROM Order o
                GROUP BY o.orderStatus
            """)
    List<Object[]> findOrderStatusBreakdown();

    @EntityGraph(attributePaths = {"payment"})
    @Query("""
            SELECT o FROM Order o
            JOIN o.payment p
            WHERE o.orderStatus = com.mercato.Entity.fulfillment.OrderStatus.CREATED
            AND p.status IN (
                com.mercato.Entity.fulfillment.payment.PaymentStatus.INITIATED,
                com.mercato.Entity.fulfillment.payment.PaymentStatus.FAILED,
                com.mercato.Entity.fulfillment.payment.PaymentStatus.USER_DROPPED
            )
            AND o.createdAt < :cutoff
            """)
    List<Order> findAbandonedUnpaidOrders(@Param("cutoff") Instant cutoff);
}
