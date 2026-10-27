package com.mercato.Service;

import com.mercato.Entity.AppRole;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.fulfillment.OrderLineStatus;
import com.mercato.Entity.fulfillment.OrderStatus;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
import com.mercato.ExceptionHandler.ForbiddenOperationException;
import com.mercato.Payloads.Response.*;
import com.mercato.Repository.OrderLineRepository;
import com.mercato.Repository.OrderRepository;
import com.mercato.Repository.ProductRepository;
import com.mercato.Repository.UserRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderLineRepository orderLineRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuthUtil authUtil;
    private final MarketplaceFinanceService finance;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardStatsDTO getAdminDashboardStats() {
        EcommUser requester = authUtil.getLoggedInUser();
        if (!requester.isAdmin()) {
            throw new ForbiddenOperationException("You are not authorized to access admin dashboard");
        }

        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfToday = startOfToday.plus(1, ChronoUnit.DAYS);
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();

        long totalUsers = userRepository.count();
        long totalSellers = userRepository.countByRole(AppRole.ROLE_SELLER);
        long totalAdmins = userRepository.countByRole(AppRole.ROLE_ADMIN);
        long newThisMonth = userRepository.countByCreatedAtBetween(startOfMonth, endOfToday);

        AdminDashboardStatsDTO.UserStats userStats = new AdminDashboardStatsDTO.UserStats(
                totalUsers, totalSellers, totalAdmins, newThisMonth
        );

        long totalOrders = orderRepository.countTotalOrders();
        long todayCount = orderRepository.countOrdersCreatedBetween(startOfToday, endOfToday);

        long pendingPayment = orderRepository.countByPaymentStatusIn(List.of(
                PaymentStatus.PENDING,
                PaymentStatus.INITIATED
        ));

        long activeOrders = orderRepository.countByOrderStatusIn(List.of(
                OrderStatus.CONFIRMED,
                OrderStatus.FULFILLMENT_PROCESSING
        ));
        BigDecimal averageOrderValue = orderRepository.findAverageOrderValue();

        OrderStats orderStats = new OrderStats(
                totalOrders, todayCount, pendingPayment, activeOrders, averageOrderValue
        );

        BigDecimal allTimeRevenue = finance.netEarnings(requester,true,Instant.EPOCH,Instant.parse("9999-12-31T00:00:00Z"));
        BigDecimal thisMonthRevenue = finance.netEarnings(requester,true,startOfMonth,endOfToday);
        BigDecimal todayRevenue = finance.netEarnings(requester,true,startOfToday,endOfToday);
        BigDecimal averageRevenuePerOrder = totalOrders==0?BigDecimal.ZERO:allTimeRevenue.divide(BigDecimal.valueOf(totalOrders),2,java.math.RoundingMode.HALF_UP);

        RevenueStats revenueStats = new RevenueStats(
                allTimeRevenue, thisMonthRevenue, todayRevenue, averageRevenuePerOrder
        );

        List<TopSellingProductDTO> topSellingProducts =
                orderLineRepository.findTopSellingProducts()
                        .stream()
                        .map(row -> new TopSellingProductDTO(
                                (String) row[0],
                                (String) row[1],
                                ((Number) row[2]).intValue(),
                                (BigDecimal) row[3]
                        ))
                        .toList();

        Map<String, Long> orderStatusBreakdown = orderRepository.findOrderStatusBreakdown()
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        List<AdminDashboardStatsDTO.RecentOrderDTO> recentOrders = orderRepository.findRecentOrders(10)
                .stream()
                .map(o -> new AdminDashboardStatsDTO.RecentOrderDTO(
                        o.getOrderId(),
                        o.getCustomerName(),
                        o.getCustomerEmail(),
                        o.getOrderStatus().name(),
                        o.getTotalAmount(),
                        o.getCreatedAt()
                ))
                .toList();

        return new AdminDashboardStatsDTO(
                userStats,
                orderStats,
                revenueStats,
                topSellingProducts,
                orderStatusBreakdown,
                recentOrders
        );
    }

    @Override
    @Transactional
    public SellerDashboardStatsDTO getSellerDashboardStats() {
        EcommUser seller = authUtil.getLoggedInUser();
        if (!seller.isSeller() && !seller.isAdmin()) {
            throw new ForbiddenOperationException(
                    "You are not authorized to access seller dashboard"
            );
        }
        String sellerEmail = seller.getEmail();

        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfToday = startOfToday.plus(1, ChronoUnit.DAYS);
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();

        BigDecimal allTimeRevenue = finance.netEarnings(seller,false,Instant.EPOCH,Instant.parse("9999-12-31T00:00:00Z"));
        BigDecimal thisMonthRevenue = finance.netEarnings(seller,false,startOfMonth,endOfToday);
        BigDecimal todayRevenue = finance.netEarnings(seller,false,startOfToday,endOfToday);
        BigDecimal averageRevenuePerOrder = orderLineRepository.countTotalOrdersBySeller(sellerEmail)==0?BigDecimal.ZERO:allTimeRevenue.divide(BigDecimal.valueOf(orderLineRepository.countTotalOrdersBySeller(sellerEmail)),2,java.math.RoundingMode.HALF_UP);

        RevenueStats revenueStats = new RevenueStats(
                allTimeRevenue, thisMonthRevenue, todayRevenue, averageRevenuePerOrder
        );

        long totalOrders = orderLineRepository.countTotalOrdersBySeller(sellerEmail);
        long todayCount = orderLineRepository.countOrdersBySellerCreatedBetween(sellerEmail, startOfToday, endOfToday);
        long pendingPayment = orderLineRepository.countPendingPaymentBySeller(
                sellerEmail,
                List.of(
                        PaymentStatus.PENDING,
                        PaymentStatus.INITIATED
                ));
        long activeOrders = orderLineRepository.countActiveOrdersBySeller(
                sellerEmail,
                List.of(
                        OrderLineStatus.CONFIRMED,
                        OrderLineStatus.PROCESSING,
                        OrderLineStatus.PARTIALLY_PROCESSED
                ));
        BigDecimal averageOrderValue = orderLineRepository.findAverageOrderValueBySeller(sellerEmail);

        OrderStats fulfillmentStats = new OrderStats(
                totalOrders, todayCount, pendingPayment, activeOrders, averageOrderValue
        );


        List<TopSellingProductDTO> topSellingProducts =
                orderLineRepository.findTopSellingProductsBySeller(sellerEmail)
                        .stream()
                        .map(row -> new TopSellingProductDTO(
                                (String) row[0],
                                (String) row[1],
                                ((Number) row[2]).intValue(),
                                (BigDecimal) row[3]
                        ))
                        .toList();

        List<SellerDashboardStatsDTO.InventoryAlertDTO> inventoryAlerts =
                productRepository.findLowStockProductsBySeller(sellerEmail, 5)
                        .stream()
                        .map(p -> new SellerDashboardStatsDTO.InventoryAlertDTO(
                                p.getProductId(),
                                p.getProductName(),
                                p.getPhysicalQty(),
                                p.getReservedQty(),
                                p.getAvailableQty()
                        ))
                        .toList();

        Map<String, Long> statusBreakdown = orderLineRepository
                .findOrderLineStatusBreakdownBySeller(sellerEmail)
                .stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        return new SellerDashboardStatsDTO(
                revenueStats,
                fulfillmentStats,
                topSellingProducts,
                inventoryAlerts,
                statusBreakdown
        );
    }
}
