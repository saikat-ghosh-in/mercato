package com.mercato.Configuration;
import com.mercato.Entity.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;

/** Synthetic, disposable earnings for reviewing finance and promotion booking. No gateway call. */
@Component @Profile("demo & !prod") @Order(3) @RequiredArgsConstructor
public class DemoEarningsInitializer implements CommandLineRunner {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;
    @Override @Transactional public void run(String...args) {
        if(orders.findByOrderId("DEMO-EARNINGS-001").isPresent()) return;
        var seller=users.findByUsername("reviewer").orElseThrow();
        var product=products.findAllBySellerUserId(seller.getUserId(),org.springframework.data.domain.Sort.by("id")).stream().findFirst().orElseThrow();
        var order=new com.mercato.Entity.fulfillment.Order();
        order.setOrderId("DEMO-EARNINGS-001");order.setCustomerName("Synthetic demo buyer");order.setCustomerEmail("synthetic-buyer@mercato.invalid");order.setOrderStatus(OrderStatus.CREATED);
        order.setRecipientName("Synthetic demo buyer");order.setRecipientPhone("9999999999");order.setDeliveryAddressLine1("Synthetic demo address — no delivery");order.setDeliveryCity("Bengaluru");order.setDeliveryState("Karnataka");order.setDeliveryPincode("560001");order.setCurrency("INR");
        BigDecimal amount=product.getSellingPrice().multiply(new BigDecimal("2"));order.setSubtotal(amount);order.setCharges(new BigDecimal("12"));order.setPlatformFee(new BigDecimal("12"));order.setShippingCharge(BigDecimal.ZERO);order.setTotalAmount(amount.add(order.getCharges()));
        var payment=new Payment();payment.setAmount(order.getTotalAmount());payment.setCurrency("INR");payment.setPaymentMethod(PaymentMethod.UPI);payment.setStatus(PaymentStatus.SUCCESS);payment.setCompletedAt(Instant.now());payment.setGatewayName("synthetic-demo-only");order.attachPayment(payment);
        payment.setCapturedAmount(order.getTotalAmount());payment.setCapturedAt(Instant.now());
        payment.setCaptureVerifiedAt(payment.getCapturedAt());payment.setCaptureEvidenceId("synthetic-demo-only");payment.setCapturedAtProvenance("OBSERVED_VERIFICATION");
        order.setAllocationCompletedAt(Instant.now());order.setPaymentStatus(PaymentStatus.SUCCESS);order.setOrderStatus(OrderStatus.FULFILLMENT_COMPLETE);
        order.addOrderLine(OrderLine.builder().fulfillmentId("DEMO-FULFILLMENT-001").orderLineNumber(1).productId(product.getProductId()).productName(product.getProductName()+" — synthetic demo sale").unitPrice(product.getSellingPrice()).orderedQty(2).shippedQty(2).lineTotal(amount).revenue(amount).sellerName(seller.getSellerDisplayName()).sellerEmail(seller.getEmail()).commissionRatePercent(new BigDecimal("5.00")).orderLineStatus(OrderLineStatus.FULFILLED).build());
        orders.save(order);
    }
}
