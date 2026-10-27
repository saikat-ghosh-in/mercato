package com.mercato.Service;

import com.mercato.Entity.*;
import com.mercato.Commerce.UnitFinancials;
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
        return FulfillmentFunding.provenCollection(order);
    }
    private static BigDecimal units(OrderLine l,int qty) {return money(l.getUnitPrice().multiply(BigDecimal.valueOf(qty)));}
    private static class Bucket {
        BigDecimal booked=BigDecimal.ZERO,fulfilled=BigDecimal.ZERO,returned=BigDecimal.ZERO,cancelled=BigDecimal.ZERO,commission=BigDecimal.ZERO,unknown=BigDecimal.ZERO,refunds=BigDecimal.ZERO,hold=BigDecimal.ZERO,promotion=BigDecimal.ZERO,sellerDelivery=BigDecimal.ZERO,reversals=BigDecimal.ZERO,pending=BigDecimal.ZERO;
        String seller;
        Map<String,Object> result() {
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("seller",seller==null?"All sellers":seller);out.put("paidMerchandise",money(booked));out.put("fulfilledSales",money(fulfilled));out.put("cancelledMerchandise",money(cancelled));out.put("commission",money(commission));out.put("unpricedLegacySales",money(unknown));out.put("refunds",money(refunds));out.put("refundHold",money(hold));out.put("promotionFees",money(promotion));
            out.put("refundedFulfilledSales",money(returned));out.put("commissionReversals",money(reversals));out.put("pendingRefunds",money(pending));
            out.put("sellerPaidDelivery",money(sellerDelivery));
            out.put("knownNetEarnings",money(fulfilled.subtract(returned).subtract(unknown).subtract(commission).subtract(hold).subtract(promotion).subtract(sellerDelivery)));
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
        return calculate(actor,admin,start,end,a,b);
    }
    /** Internal worker entry: the persisted recipient determines scope, never a caller-supplied admin flag. */
    @Transactional(readOnly=true)
    public Map<String,Object> monthlyReport(String userId,YearMonth month,ZoneId zone){
        var actor=users.findByUserId(userId).orElseThrow();
        if(!com.mercato.Mail.MonthlyReports.eligible(actor))throw new AccessDeniedException("Report recipient is no longer eligible");
        return calculate(actor,actor.isAdmin(),month.atDay(1),month.atEndOfMonth(),month.atDay(1).atStartOfDay(zone).toInstant(),month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
    }
    @Transactional(readOnly=true)
    public BigDecimal availableBudget(EcommUser seller){return money(netEarnings(seller,false,Instant.EPOCH,Instant.parse("9999-12-31T00:00:00Z")).max(BigDecimal.ZERO));}
    @Transactional(readOnly=true)
    @SuppressWarnings("unchecked")
    public BigDecimal netEarnings(EcommUser actor,boolean admin,Instant from,Instant through){
        var data=calculate(actor,admin,from.atZone(ZoneOffset.UTC).toLocalDate(),through.atZone(ZoneOffset.UTC).toLocalDate(),from,through);
        return (BigDecimal)((Map<String,Object>)data.get("totals")).get("knownNetEarnings");
    }
    private Map<String,Object> calculate(EcommUser actor,boolean admin,LocalDate start,LocalDate end,Instant a,Instant b){
        Bucket total=new Bucket(); Map<String,Bucket> sellers=new LinkedHashMap<>();List<Map<String,Object>> ledger=new ArrayList<>();
        BigDecimal captured=BigDecimal.ZERO,refunded=BigDecimal.ZERO,platform=BigDecimal.ZERO,shipping=BigDecimal.ZERO,unknownCharges=BigDecimal.ZERO,pendingRefunds=BigDecimal.ZERO,unverifiedLegacyCollections=BigDecimal.ZERO,handling=BigDecimal.ZERO;
        for(var order:orders.financialOrders(a,b,admin?null:actor.getEmail())) {
            if(!paid(order)) {if(admin && order.getPayment()!=null && (order.getPayment().getCapturedAt()!=null || order.getPaymentStatus()==PaymentStatus.SUCCESS))unverifiedLegacyCollections=unverifiedLegacyCollections.add(order.getTotalAmount());continue;}
            // Actual collection and entitlement are different facts. A partial verified collection
            // never books the original full merchandise or makes it available for promotions.
            if(!FulfillmentFunding.verifiedCollection(order)){
                if(admin){captured=captured.add(order.getPayment().getCapturedAmount());for(var r:order.getPayment().getRefunds()){if(r.getStatus()==RefundStatus.SUCCESS)refunded=refunded.add(r.getAmount());else if(r.committed())pendingRefunds=pendingRefunds.add(r.getAmount());}}
                continue;
            }
            var refund=order.getPayment().getRefund();
            BigDecimal refundAmount=order.getPayment().getRefunds().stream().filter(r->r.getStatus()==RefundStatus.SUCCESS).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal cancelValue=order.getOrderLines().stream().map(l->units(l,l.getCancelledQty())).reduce(BigDecimal.ZERO,BigDecimal::add);
            boolean allCancelled=order.getOrderLines().stream().allMatch(l->l.getCancelledQty()==l.getOrderedQty());
            boolean fullRefund=order.isAutomaticFullRefund() || (order.getTotalAmount().signum()>0 && refundAmount.compareTo(order.getTotalAmount())>=0);
            BigDecimal refundedCharges=allCancelled||fullRefund?refundAmount.subtract(cancelValue).max(BigDecimal.ZERO).min(money(order.getCharges())):BigDecimal.ZERO;
            // Out-of-band refunds have no reliable line attribution. Reserve the excess across shipped sales.
            BigDecimal unallocated=refundAmount.subtract(cancelValue).subtract(refundedCharges).max(BigDecimal.ZERO);
            BigDecimal shipped=order.getOrderLines().stream().map(l->units(l,l.getShippedQty())).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(admin) {
                if(order.getPayment().getCapturedAt()!=null)captured=captured.add(order.getPayment().getCapturedAmount());
                else unverifiedLegacyCollections=unverifiedLegacyCollections.add(order.getTotalAmount());
                refunded=refunded.add(refundAmount);
                pendingRefunds=pendingRefunds.add(order.getPayment().getRefunds().stream().filter(r->Set.of(RefundStatus.PENDING,RefundStatus.ONHOLD).contains(r.getStatus())).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
                BigDecimal remainingCharges=money(order.getCharges()).subtract(refundedCharges).max(BigDecimal.ZERO);
                if(UnitFinancials.attributed(order)) {
                    long p=0,d=0,h=0;
                    for(var u:UnitFinancials.units(order)){p+=u.getPlatformPaise();h+=u.getHandlingPaise();d+=u.getBuyerNormalDeliveryPaise()+u.getBuyerExpeditedDeliveryPaise();}
                    for(var r:order.getPayment().getRefunds())if(r.committed())for(var allocation:r.getAllocations()){p-=allocation.getPlatformPaise();h-=allocation.getHandlingPaise();d-=allocation.getDeliveryPaise();}
                    handling=handling.add(BigDecimal.valueOf(Math.max(0,h),2));platform=platform.add(BigDecimal.valueOf(Math.max(0,p),2));shipping=shipping.add(BigDecimal.valueOf(Math.max(0,d),2));
                } else
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
                BigDecimal charge=com.mercato.Utils.CommissionMath.forUnits(line.getUnitPrice(),fullRefund?0:line.getShippedQty(),line.getCommissionRatePercent(),line.getCommissionCapPerItem());
                BigDecimal delivery = line.getSellerDeliveryCharge() == null ? BigDecimal.ZERO : money(line.getSellerDeliveryCharge().multiply(BigDecimal.valueOf(line.getShippedQty())).divide(BigDecimal.valueOf(line.getOrderedQty()), 10, RoundingMode.HALF_UP));
                BigDecimal reversal=BigDecimal.ZERO,linePending=BigDecimal.ZERO;
                BigDecimal unknown=line.getCommissionRatePercent()==null?gross.subtract(returned):BigDecimal.ZERO;
                BigDecimal cancelled=units(line,line.getCancelledQty());
                BigDecimal allocated=cancelValue.signum()==0?BigDecimal.ZERO:money(refundAmount.min(cancelValue).multiply(cancelled).divide(cancelValue,10,RoundingMode.HALF_UP));
                BigDecimal hold=fullRefund||shipped.signum()==0?BigDecimal.ZERO:money(unallocated.multiply(gross).divide(shipped,10,RoundingMode.HALF_UP));
                if(!UnitFinancials.attributed(line) && !fullRefund && order.getPayment().getRefunds().stream().anyMatch(r->r.committed()&&r.getAllocations().isEmpty()))hold=gross.subtract(returned).subtract(charge).subtract(unknown).subtract(delivery).max(BigDecimal.ZERO);
                if(!line.getMoneyUnits().isEmpty() && !UnitFinancials.attributed(line)){unknown=gross;charge=BigDecimal.ZERO;delivery=BigDecimal.ZERO;}
                if(UnitFinancials.attributed(line)) {
                    gross=BigDecimal.ZERO;returned=BigDecimal.ZERO;charge=BigDecimal.ZERO;delivery=BigDecimal.ZERO;unknown=BigDecimal.ZERO;allocated=BigDecimal.ZERO;cancelled=BigDecimal.ZERO;hold=BigDecimal.ZERO;
                    for(var unit:line.getMoneyUnits()) {
                        String stage=unit.getState().getStage();
                        if(stage.equals("CANCELLED"))cancelled=cancelled.add(BigDecimal.valueOf(unit.getNetMerchandisePaise(),2));
                        for(var r:order.getPayment().getRefunds())if(r.committed())for(var allocation:r.getAllocations())if(allocation.getUnit().getUnitId().equals(unit.getUnitId())){
                            if(r.getStatus()==RefundStatus.SUCCESS)allocated=allocated.add(BigDecimal.valueOf(allocation.totalPaise(),2));else linePending=linePending.add(BigDecimal.valueOf(allocation.totalPaise(),2));
                        }
                        if(!Set.of("SHIPPED","RETURNED").contains(stage))continue;
                        gross=gross.add(BigDecimal.valueOf(unit.getNetMerchandisePaise(),2));
                        long refundedMerchandise=Math.min(unit.getNetMerchandisePaise(),UnitFinancials.merchandiseCommitted(order,unit.getUnitId(),false));
                        returned=returned.add(BigDecimal.valueOf(refundedMerchandise,2));
                        delivery=delivery.add(BigDecimal.valueOf(unit.getSellerDeliveryPaise(),2));
                        if(unit.getCommissionPaise()==null)unknown=unknown.add(BigDecimal.valueOf(unit.getNetMerchandisePaise()-refundedMerchandise,2));
                        else {
                            long reversed=unit.getNetMerchandisePaise()==0?0:BigDecimal.valueOf(unit.getCommissionPaise()).multiply(BigDecimal.valueOf(refundedMerchandise)).divide(BigDecimal.valueOf(unit.getNetMerchandisePaise()),0,RoundingMode.HALF_UP).longValueExact();
                            reversed=Math.min(unit.getCommissionPaise(),reversed);reversal=reversal.add(BigDecimal.valueOf(reversed,2));charge=charge.add(BigDecimal.valueOf(unit.getCommissionPaise()-reversed,2));
                        }
                    }
                    boolean unattributed=order.getPayment().getRefunds().stream().anyMatch(r->r.committed() && r.getAllocations().stream().mapToLong(RefundUnitAllocation::totalPaise).sum()!=com.mercato.Commerce.OrderMoneySnapshot.paise(r.getAmount()));
                    if(unattributed)hold=gross.subtract(returned).subtract(charge).subtract(unknown).max(BigDecimal.ZERO);
                }
                for(var dest:List.of(bucket,total)) {
                    dest.booked=dest.booked.add(line.getLineTotal());dest.fulfilled=dest.fulfilled.add(gross);dest.cancelled=dest.cancelled.add(cancelled);dest.commission=dest.commission.add(charge);dest.unknown=dest.unknown.add(unknown);dest.refunds=dest.refunds.add(allocated);dest.hold=dest.hold.add(hold);
                    dest.returned=dest.returned.add(returned);
                    dest.sellerDelivery=dest.sellerDelivery.add(delivery);dest.reversals=dest.reversals.add(reversal);dest.pending=dest.pending.add(linePending);
                }
                if(ledger.size()<100) {
                    Map<String,Object> row=new LinkedHashMap<>();row.put("orderId",order.getOrderId());row.put("product",line.getProductName());row.put("seller",line.getSellerName());row.put("fulfilled",gross);row.put("refunded",returned);row.put("commissionRate",line.getCommissionRatePercent());row.put("commissionCapPerItem",line.getCommissionCapPerItem());row.put("commission",charge);row.put("sellerPaidDelivery",delivery);row.put("netBeforePromotions",line.getCommissionRatePercent()==null?null:money(gross.subtract(returned).subtract(charge).subtract(hold).subtract(delivery)));row.put("createdAt",order.getCreatedAt());ledger.add(row);
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
        Map<String,Object> result=new LinkedHashMap<>();result.put("from",start);result.put("through",end);result.put("totals",total.result());result.put("sellerBreakdown",sellers.values().stream().map(Bucket::result).toList());result.put("lines",ledger);result.put("capturedPayments",money(captured));result.put("successfulRefunds",money(refunded));result.put("pendingRefunds",money(pendingRefunds));result.put("platformFees",money(platform));result.put("shippingCollected",money(shipping));result.put("handlingFees",money(handling));result.put("unclassifiedLegacyCharges",money(unknownCharges));result.put("promotionBookings",money(bookedPromotions));result.put("promotionCredits",money(credits));
        result.put("unverifiedLegacyCollections",money(unverifiedLegacyCollections));result.put("recognitionBasis","Verified captureVerifiedAt observation cohort; current attributed fulfillment and committed refund state. Halted proven collections earn nothing. Legacy capture claims without reader proof remain unverified. Promotion bookings/credits are period movements.");
        result.put("platformAccrualBeforeCosts",money(total.commission.add(platform).add(handling)));result.put("bankPayoutsAvailable",false);
        return result;
    }
}
