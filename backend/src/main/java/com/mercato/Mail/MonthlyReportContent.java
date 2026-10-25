package com.mercato.Mail;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.math.BigDecimal;

/** Explicit allowlist of aggregate fields: no customer details, ledger rows or seller breakdowns. */
public final class MonthlyReportContent {
    private MonthlyReportContent(){}
    public static String build(YearMonth month,boolean admin,Map<String,Object> report,long products,long lowStock){
        var text=new StringBuilder("Mercato · ").append(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ENGLISH)))
            .append(admin?"\nMarketplace monthly report\n":"\nYour monthly seller report\n");
        Map<?,?> totals=report.get("totals") instanceof Map<?,?> values?values:Map.of();
        if(admin){amount(text,"Verified captures",report.get("capturedPayments"));amount(text,"Successful refunds",report.get("successfulRefunds"));amount(text,"Buyer platform fees",report.get("platformFees"));amount(text,"Buyer shipping collected",report.get("shippingCollected"));}
        amount(text,"Fulfilled sales",totals.get("fulfilledSales"));amount(text,"Refunded fulfilled sales",totals.get("refundedFulfilledSales"));
        amount(text,"Commission",totals.get("commission"));amount(text,"Seller-paid delivery",totals.get("sellerPaidDelivery"));
        amount(text,"Promotion fees less credits",totals.get("promotionFees"));amount(text,"Refund hold",totals.get("refundHold"));
        amount(text,"Unpriced legacy sales",totals.get("unpricedLegacySales"));amount(text,"Known net earnings",totals.get("knownNetEarnings"));
        return text.append("\nProducts currently listed: ").append(products).append("\nLow/out-of-stock listings (5 units or fewer): ").append(lowStock)
            .append("\n\nSales cover orders created in the previous calendar month, using their current fulfillment and refund status. Product counts reflect the time this report was prepared.")
            .append("\nKnown net earnings are an accrual estimate, not a bank payout. Review the dashboard for order details and unresolved amounts.")
            .append("\nThis operational report is sent monthly, including months with no activity.\n").toString();
    }
    private static void amount(StringBuilder text,String label,Object value){
        BigDecimal number=value instanceof BigDecimal decimal?decimal:BigDecimal.ZERO;
        text.append(label).append(": ₹").append(number.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()).append('\n');
    }
}
