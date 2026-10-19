package com.mercato.Service;

import com.mercato.Entity.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Repository.*;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class MarketplaceFinanceService {
    private final OrderRepository orders;
    private final SellerPromotionRepository promotions;
    private final AuthUtil auth;
    private final UserRepository users;
    public static BigDecimal money(BigDecimal value) {return (value==null?BigDecimal.ZERO:value).setScale(2,RoundingMode.HALF_UP);}
    public static BigDecimal commission(BigDecimal gross, BigDecimal rate) { return money(gross.multiply(rate).divide(BigDecimal.valueOf(100))); }
    public static boolean paid(Order order) {
        return order.getPayment()!=null && (order.getPayment().getCompletedAt()!=null || Set.of(PaymentStatus.SUCCESS,PaymentStatus.REFUNDED,PaymentStatus.PARTIALLY_REFUNDED,PaymentStatus.REFUND_INITIATED).contains(order.getPaymentStatus()));
    }
    private static BigDecimal units(OrderLine l,int qty) {return money(l.getUnitPrice().multiply(BigDecimal.valueOf(qty)));}
    private static class Bucket {
        BigDecimal booked=BigDecimal.ZERO,fulfilled=BigDecimal.ZERO,returned=BigDecimal.ZERO,cancelled=BigDecimal.ZERO,commission=BigDecimal.ZERO,unknown=BigDecimal.ZERO,refunds=BigDecimal.ZERO,hold=BigDecimal.ZERO,promotion=BigDecimal.ZERO;
        String seller;
        Map<String,Object> result() {
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("seller",seller==null?"All sellers":seller);out.put("paidMerchandise",money(booked));out.put("fulfilledSales",money(fulfilled));out.put("cancelledMerchandise",money(cancelled));out.put("commission",money(commission));out.put("unpricedLegacySales",money(unknown));out.put("refunds",money(refunds));out.put("refundHold",money(hold));out.put("promotionFees",money(promotion));
            out.put("refundedFulfilledSales",money(returned));
            out.put("knownNetEarnings",money(fulfilled.subtract(returned).subtract(unknown).subtract(commission).subtract(hold).subtract(promotion)));
            return out;
        }
    }
    @Transactional(readOnly=true)
    public Object report(LocalDate from, LocalDate through, boolean admin) {
        var actor=auth.getLoggedInUser();
        if(actor==null || (admin?!actor.isAdmin():!actor.isSeller()&&!actor.isAdmin())) throw new AccessDeniedException("Dashboard permission required");
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        LocalDate end=through==null?today:through, start=from==null?end.minusDays(89):from;
        if(start.isAfter(end) || ChronoUnit.DAYS.between(start,end)>365 || end.isAfter(today)) throw new IllegalArgumentException("Choose up to 366 days, ending no later than today (UTC)");
        Instant a=start.atStartOfDay(ZoneOffset.UTC).toInstant(), b=end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Bucket total=new Bucket(); Map<String,Bucket> sellers=new LinkedHashMap<>();List<Map<String,Object>> ledger=new ArrayList<>();
        BigDecimal captured=BigDecimal.ZERO,refunded=BigDecimal.ZERO,platform=BigDecimal.ZERO,shipping=BigDecimal.ZERO,unknownCharges=BigDecimal.ZERO,pendingRefunds=BigDecimal.ZERO;
        for(var order:orders.financialOrders(a,b,admin?null:actor.getEmail())) {
            if(!paid(order)) continue;
            var refund=order.getPayment().getRefund();
            BigDecimal refundAmount=refund!=null && refund.getStatus()==RefundStatus.SUCCESS?money(refund.getAmount()):BigDecimal.ZERO;
            BigDecimal cancelValue=order.getOrderLines().stream().map(l->units(l,l.getCancelledQty())).reduce(BigDecimal.ZERO,BigDecimal::add);
            boolean allCancelled=order.getOrderLines().stream().allMatch(l->l.getCancelledQty()==l.getOrderedQty());
            boolean fullRefund=order.getTotalAmount().signum()>0 && refundAmount.compareTo(order.getTotalAmount())>=0;
            BigDecimal refundedCharges=allCancelled||fullRefund?refundAmount.subtract(cancelValue).max(BigDecimal.ZERO).min(money(order.getCharges())):BigDecimal.ZERO;
            // Out-of-band refunds have no reliable line attribution. Reserve the excess across shipped sales.
            BigDecimal unallocated=refundAmount.subtract(cancelValue).subtract(refundedCharges).max(BigDecimal.ZERO);
            BigDecimal shipped=order.getOrderLines().stream().map(l->units(l,l.getShippedQty())).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(admin) {
                captured=captured.add(order.getTotalAmount());refunded=refunded.add(refundAmount);
                if(refund!=null && Set.of(RefundStatus.PENDING,RefundStatus.ONHOLD).contains(refund.getStatus())) pendingRefunds=pendingRefunds.add(refund.getAmount());
                BigDecimal remainingCharges=money(order.getCharges()).subtract(refundedCharges).max(BigDecimal.ZERO);
                if(order.getPlatformFee()==null || order.getShippingCharge()==null) unknownCharges=unknownCharges.add(remainingCharges);
                else {
                    BigDecimal ratio=money(order.getCharges()).signum()==0?BigDecimal.ZERO:remainingCharges.divide(order.getCharges(),10,RoundingMode.HALF_UP);
                    platform=platform.add(money(order.getPlatformFee().multiply(ratio)));shipping=shipping.add(money(order.getShippingCharge().multiply(ratio)));
                }
            }
            for(var line:order.getOrderLines()) {
                if(!admin && !actor.getEmail().equals(line.getSellerEmail())) continue;
                var bucket=sellers.computeIfAbsent(line.getSellerEmail(),key->{Bucket value=new Bucket();value.seller=line.getSellerName();return value;});
                BigDecimal gross=units(line,line.getShippedQty());
                BigDecimal returned=fullRefund?gross:BigDecimal.ZERO;
                BigDecimal charge=line.getCommissionRatePercent()==null?BigDecimal.ZERO:commission(gross.subtract(returned),line.getCommissionRatePercent());
                BigDecimal unknown=line.getCommissionRatePercent()==null?gross.subtract(returned):BigDecimal.ZERO;
                BigDecimal cancelled=units(line,line.getCancelledQty());
                BigDecimal allocated=cancelValue.signum()==0?BigDecimal.ZERO:money(refundAmount.min(cancelValue).multiply(cancelled).divide(cancelValue,10,RoundingMode.HALF_UP));
                BigDecimal hold=fullRefund||shipped.signum()==0?BigDecimal.ZERO:money(unallocated.multiply(gross).divide(shipped,10,RoundingMode.HALF_UP));
                for(var dest:List.of(bucket,total)) {
                    dest.booked=dest.booked.add(line.getLineTotal());dest.fulfilled=dest.fulfilled.add(gross);dest.cancelled=dest.cancelled.add(cancelled);dest.commission=dest.commission.add(charge);dest.unknown=dest.unknown.add(unknown);dest.refunds=dest.refunds.add(allocated);dest.hold=dest.hold.add(hold);
                    dest.returned=dest.returned.add(returned);
                }
                if(ledger.size()<100) {
                    Map<String,Object> row=new LinkedHashMap<>();row.put("orderId",order.getOrderId());row.put("product",line.getProductName());row.put("seller",line.getSellerName());row.put("fulfilled",gross);row.put("refunded",returned);row.put("commissionRate",line.getCommissionRatePercent());row.put("commission",charge);row.put("netBeforePromotions",line.getCommissionRatePercent()==null?null:money(gross.subtract(returned).subtract(charge).subtract(hold)));row.put("createdAt",order.getCreatedAt());ledger.add(row);
                }
            }
        }
        var campaigns=admin?promotions.findAll():promotions.findBySellerIdOrderByCreatedAtDesc(actor.getUserId());
        BigDecimal bookedPromotions=BigDecimal.ZERO,credits=BigDecimal.ZERO;
        for(var campaign:campaigns) {
            // Bookings and credits are separate period movements; cancellation can happen in a later period.
            BigDecimal movement=BigDecimal.ZERO;
            if(!campaign.getCreatedAt().isBefore(a)&&campaign.getCreatedAt().isBefore(b)) {bookedPromotions=bookedPromotions.add(campaign.getFee());movement=movement.add(campaign.getFee());}
            if(campaign.getCancelledAt()!=null&&!campaign.getCancelledAt().isBefore(a)&&campaign.getCancelledAt().isBefore(b)) {credits=credits.add(campaign.getCredited());movement=movement.subtract(campaign.getCredited());}
            if(movement.signum()!=0) {
                var owner=users.findByUserId(campaign.getSellerId()).orElse(null);
                String key=owner==null?campaign.getSellerId():owner.getEmail();
                var bucket=sellers.computeIfAbsent(key,k->{Bucket v=new Bucket();v.seller=owner==null?"Former seller":owner.getSellerDisplayName();return v;});
                bucket.promotion=bucket.promotion.add(movement);
            }
        }
        total.promotion=bookedPromotions.subtract(credits);
        Map<String,Object> result=new LinkedHashMap<>();result.put("from",start);result.put("through",end);result.put("totals",total.result());result.put("sellerBreakdown",sellers.values().stream().map(Bucket::result).toList());result.put("lines",ledger);result.put("capturedPayments",money(captured));result.put("successfulRefunds",money(refunded));result.put("pendingRefunds",money(pendingRefunds));result.put("platformFees",money(platform));result.put("shippingCollected",money(shipping));result.put("unclassifiedLegacyCharges",money(unknownCharges));result.put("promotionBookings",money(bookedPromotions));result.put("promotionCredits",money(credits));
        result.put("platformAccrualBeforeCosts",money(total.commission.add(platform)));result.put("bankPayoutsAvailable",false);
        return result;
    }
}
