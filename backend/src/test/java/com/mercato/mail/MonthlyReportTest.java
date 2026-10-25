package com.mercato.mail;
import com.mercato.Mail.MonthlyReports;
import com.mercato.Mail.MonthlyReportContent;
import com.mercato.Entity.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MonthlyReportTest {
    @Test void monthlyWindowUsesTheBusinessTimezoneAcrossYearBoundaries(){
        assertEquals(YearMonth.of(2025,12),MonthlyReports.reportMonth(Clock.fixed(Instant.parse("2025-12-31T20:00:00Z"),ZoneOffset.UTC),ZoneId.of("Asia/Kolkata")));
    }
    @Test void onlyVerifiedActiveAdminsAndApprovedSellersReceiveReports(){
        var seller=EcommUser.builder().enabled(true).emailVerified(true).sellerStatus(SellerStatus.APPROVED).roles(Set.of(new Role(null,AppRole.ROLE_SELLER))).build();
        assertTrue(MonthlyReports.eligible(seller));seller.setSellerSuspended(true);assertFalse(MonthlyReports.eligible(seller));
        seller.setSellerSuspended(false);seller.setSellerStatus(SellerStatus.PENDING);assertFalse(MonthlyReports.eligible(seller));
        seller.setRoles(Set.of(new Role(null,AppRole.ROLE_ADMIN)));assertTrue(MonthlyReports.eligible(seller));
        seller.setEmailVerified(false);assertFalse(MonthlyReports.eligible(seller));
    }
    @Test void sellerContentIncludesFinancialComponentsButNoOtherSellersOrCustomerData(){
        var totals=Map.<String,Object>of("fulfilledSales",new BigDecimal("1000.00"),"commission",new BigDecimal("5.00"),"knownNetEarnings",new BigDecimal("995.00"));
        var report=Map.<String,Object>of("totals",totals,"capturedPayments",new BigDecimal("987654.00"),"sellerBreakdown",List.of("Other seller private"),"lines",List.of("Customer private"));
        String text=MonthlyReportContent.build(YearMonth.of(2026,9),false,report,3,1);
        assertTrue(text.contains("1000.00"));assertTrue(text.contains("5.00"));assertTrue(text.contains("995.00"));
        assertFalse(text.contains("987654"));assertFalse(text.contains("Other seller"));assertFalse(text.contains("Customer private"));
        assertTrue(text.contains("not a bank payout"));
    }
    @Test void zeroActivityStillProducesAUsefulMonthlyReport(){
        String text=MonthlyReportContent.build(YearMonth.of(2026,9),true,Map.of("totals",Map.of()),0,0);
        assertTrue(text.contains("0.00"));assertTrue(text.contains("Products"));assertTrue(text.contains("September 2026"));
    }
}
