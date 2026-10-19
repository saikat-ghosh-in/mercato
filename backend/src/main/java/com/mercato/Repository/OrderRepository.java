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
    @EntityGraph(attributePaths={"orderLines", "payment", "payment.refund"})
    @Query("select distinct o from Order o where o.createdAt >= :start and o.createdAt < :end and (:email is null or exists (select l.id from OrderLine l where l.order = o and l.sellerEmail = :email)) order by o.createdAt desc")
    List<Order> financialOrders(@Param("start") Instant start, @Param("end") Instant end, @Param("email") String email);

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
            SELECT CAST(COALESCE(SUM(lr.line_revenue + o.charges), 0) AS NUMERIC(15, 2))
            FROM order_snapshot o
            JOIN (
                SELECT order_fk, COALESCE(SUM(revenue), 0) AS line_revenue
                FROM order_lines_snapshot
                GROUP BY order_fk
            ) lr ON lr.order_fk = o.id
            """, nativeQuery = true)
    BigDecimal findTotalRevenue();

    @Query(value = """
            SELECT CAST(COALESCE(SUM(lr.line_revenue + o.charges), 0) AS NUMERIC(15, 2))
            FROM order_snapshot o
            JOIN (
                SELECT order_fk, COALESCE(SUM(revenue), 0) AS line_revenue
                FROM order_lines_snapshot
                GROUP BY order_fk
            ) lr ON lr.order_fk = o.id
            AND o.created_at BETWEEN :start AND :end
            """, nativeQuery = true)
    BigDecimal findTotalRevenueBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            SELECT CAST(COALESCE(AVG(lr.line_revenue + o.charges), 0) AS NUMERIC(15, 2))
            FROM order_snapshot o
            JOIN (
                SELECT order_fk, COALESCE(SUM(revenue), 0) AS line_revenue
                FROM order_lines_snapshot
                GROUP BY order_fk
            ) lr ON lr.order_fk = o.id
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
