package com.mercato.hardening;
import com.fasterxml.jackson.databind.*;
import com.mercato.Entity.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.*;
import com.mercato.Repository.*;
import com.mercato.Service.MarketplaceFinanceService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="marketplace.notices.initial-delay-ms=3600000") @ActiveProfiles("demo") @AutoConfigureMockMvc @DirtiesContext
class MarketplaceFinancePromotionTest {
    @Autowired MockMvc rawMvc; CookieBrowserHarness mvc;
    @org.junit.jupiter.api.BeforeEach void cookieBrowser(){mvc=new CookieBrowserHarness(rawMvc,json);}@Autowired ObjectMapper json;@Autowired ProductRepository products;@Autowired SellerPromotionRepository campaigns;
    @Autowired OrderRepository orders;@Autowired PlatformTransactionManager transactions;
    @Autowired UserRepository users; @Autowired RoleRepository roles;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired MarketplaceFinanceService finance;
    String login(String name,String password)throws Exception {return mvc.login(name,password);}
    JsonNode getReport(String token)throws Exception{return json.readTree(mvc.perform(get("/api/admin/finance").with(mvc.as(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    Map<String,Object> request(String product,String start){Map<String,Object> r=new HashMap<>();r.put("productId",product);r.put("tier","DISCOVERY");r.put("durationDays",3);r.put("startDate",start);r.put("expectedFee",147);r.put("requestId",UUID.randomUUID().toString());r.put("authorizeDeduction",true);return r;}
    @BeforeEach void clearCampaigns(){campaigns.deleteAll();new TransactionTemplate(transactions).executeWithoutResult(t->{var o=orders.findByOrderId("DEMO-EARNINGS-001").orElseThrow();o.establishConfirmation(Instant.now().minusSeconds(7200));o.getPayment().setRefund(null);o.getPayment().setCapturedAt(Instant.now());o.getPayment().setCapturedAmount(o.getTotalAmount());o.getPayment().setCompletedAt(Instant.now());o.getPayment().setStatus(PaymentStatus.SUCCESS);o.setPaymentStatus(PaymentStatus.SUCCESS);});jdbc.update("update order_lines_snapshot set commission_rate_percent=5 where fulfillment_id='DEMO-FULFILLMENT-001'");}
    String approvedSeller()throws Exception {new TransactionTemplate(transactions).executeWithoutResult(t->{var seller=users.findByUsername("sellerreview").orElseThrow();seller.setSellerStatus(SellerStatus.APPROVED);seller.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());});return login("sellerreview","SellerOnly!2026");}
    @Test void shipmentReadUsesExplicitScopeAndHonestDisabledCarrierState()throws Exception {
        String admin=login("reviewer","ReviewOnly!2026");
        mvc.perform(get("/api/admin/orders/DEMO-EARNINGS-001/shipments").with(mvc.as(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.shipments.length()").value(0))
            .andExpect(jsonPath("$.canRefresh").value(false));
        String shopper=login("shopperreview","ShopOnly!2026");
        mvc.perform(get("/api/admin/orders/DEMO-EARNINGS-001/shipments").with(mvc.as(shopper))).andExpect(status().isForbidden());
        mvc.perform(get("/api/user/orders/foreign-order/shipments").with(mvc.as(shopper))).andExpect(status().isNotFound());
    }
    @Test void financeSeparatesFeesUsesCapturedRatesAndExcludesUnpaidSales()throws Exception {
        String admin=login("reviewer","ReviewOnly!2026");var baseline=getReport(admin);
        assertTrue(baseline.get("totals").get("fulfilledSales").decimalValue().signum()>0);
        assertEquals(0,baseline.get("totals").get("commission").decimalValue().compareTo(MarketplaceFinanceService.commission(baseline.get("totals").get("fulfilledSales").decimalValue(),new BigDecimal("5"))));
        assertEquals(0,new BigDecimal("12").compareTo(baseline.get("platformFees").decimalValue()));
        assertEquals(0,baseline.get("platformAccrualBeforeCosts").decimalValue().compareTo(baseline.get("totals").get("commission").decimalValue().add(new BigDecimal("12"))));
        mvc.perform(get("/api/admin/finance").param("from","2020-01-01").with(mvc.as(admin))).andExpect(status().isBadRequest());
        String shopper=login("shopperreview","ShopOnly!2026");mvc.perform(get("/api/seller/finance").with(mvc.as(shopper))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/finance").with(mvc.as(shopper))).andExpect(status().isForbidden());
        new TransactionTemplate(transactions).executeWithoutResult(t->{var o=orders.findByOrderId("DEMO-EARNINGS-001").orElseThrow();o.getPayment().setCapturedAt(null);o.getPayment().setCapturedAmount(null);o.getPayment().setCompletedAt(null);o.getPayment().setStatus(PaymentStatus.INITIATED);o.setPaymentStatus(PaymentStatus.INITIATED);});
        assertEquals(0,getReport(admin).get("totals").get("fulfilledSales").decimalValue().signum());
        new TransactionTemplate(transactions).executeWithoutResult(t->{var o=orders.findByOrderId("DEMO-EARNINGS-001").orElseThrow();o.getPayment().setCapturedAt(Instant.now());o.getPayment().setCapturedAmount(o.getTotalAmount());o.getPayment().setCompletedAt(Instant.now());o.getPayment().setStatus(PaymentStatus.SUCCESS);o.setPaymentStatus(PaymentStatus.SUCCESS);});
    }
    @Test void quotesAreValidatedOwnedIdempotentAndCreditedWithoutFreePlacement()throws Exception {
        String admin=login("reviewer","ReviewOnly!2026");var product=products.findAll().get(0);String tomorrow=LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        Map<String,Object> request=request(product.getProductId(),tomorrow);
        mvc.perform(post("/api/seller/promotions").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("$.fee").value(147));
        mvc.perform(post("/api/seller/promotions").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isOk());assertEquals(1,campaigns.count());
        var id=campaigns.findAll().get(0).getId();
        mvc.perform(get("/api/public/promotions").param("placement","catalog")).andExpect(jsonPath("$.length()").value(0));
        request.put("expectedFee",0);mvc.perform(post("/api/seller/promotions").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        var overlap=request(product.getProductId(),tomorrow);mvc.perform(post("/api/seller/promotions").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(overlap))).andExpect(status().isBadRequest());
        var finance=getReport(admin);assertEquals(0,new BigDecimal("147").compareTo(finance.get("totals").get("promotionFees").decimalValue()));assertEquals(finance.get("totals").get("knownNetEarnings"),finance.get("sellerBreakdown").get(0).get("knownNetEarnings"));
        mvc.perform(post("/api/seller/promotions/"+id+"/cancel").with(mvc.as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.credited").value(147));
        mvc.perform(post("/api/seller/promotions/"+id+"/cancel").with(mvc.as(admin))).andExpect(status().isOk());assertEquals(0,getReport(admin).get("totals").get("promotionFees").decimalValue().signum());
    }
    @Test void sellerSubmissionCannotPublishOrBypassReviewViaStockOrClientStatus()throws Exception {
        String admin=login("reviewer","ReviewOnly!2026"), seller=approvedSeller();String category=new TransactionTemplate(transactions).execute(t->products.findAll().get(0).getCategory().getCategoryId());
        Map<String,Object> body=new HashMap<>(Map.of("productName","Submission test product","description","Useful product details for testing","retailPrice",100,"discountPercent",0,"active",true,"primaryImageUrl","https://images.unsplash.com/photo-1441986300917-64674bd600d8","listingStatus","APPROVED"));
        mvc.perform(post("/api/seller/categories/"+category+"/product").with(mvc.as(seller)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        body.putAll(Map.of("weightKg",1,"lengthCm",20,"widthCm",15,"heightCm",10));
        var created=json.readTree(mvc.perform(post("/api/seller/categories/"+category+"/product").with(mvc.as(seller)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isCreated()).andExpect(jsonPath("$.listingStatus").value("PENDING")).andReturn().getResponse().getContentAsString());
        String id=created.get("productId").asText();
        mvc.perform(post("/api/admin/products/"+id+"/review").with(mvc.as(seller)).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"note\":\"Cannot approve own submission\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/products/"+id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("cartItems",List.of(Map.of("productId",id,"quantity",1)))))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/products/"+id+"/review").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"note\":\"Verified synthetic test details\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/public/products/"+id)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/products/"+id+"/review").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false,\"note\":\"Duplicate review must be rejected\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/seller/products/"+id).with(mvc.as(seller)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.listingStatus").value("PENDING"));
        mvc.perform(get("/api/public/products/"+id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/seller/products/supplyUpdate").with(mvc.as(seller)).contentType(MediaType.APPLICATION_JSON).content("[{\"productId\":\""+id+"\",\"quantity\":5,\"supplyType\":\"ABSOLUTE\"}]")).andExpect(status().isOk());
        mvc.perform(get("/api/public/products/"+id)).andExpect(status().isNotFound());
    }
    @Test void promotionOwnershipBudgetAndCurrentEligibilityAreServerEnforced()throws Exception {
        String admin=login("reviewer","ReviewOnly!2026"), seller=approvedSeller();var p=products.findAll().get(0);
        var r=request(p.getProductId(),LocalDate.now(ZoneOffset.UTC).toString());
        mvc.perform(post("/api/seller/promotions").with(mvc.as(seller)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(r))).andExpect(status().isForbidden());
        mvc.perform(get("/api/seller/finance").with(mvc.as(seller))).andExpect(jsonPath("$.totals.fulfilledSales").value(0));
        r.put("authorizeDeduction",false);mvc.perform(post("/api/seller/promotions").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(r))).andExpect(status().isBadRequest());r.put("authorizeDeduction",true);
        var c=json.readTree(mvc.perform(post("/api/seller/promotions").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(r))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/seller/promotions/"+c.get("id").asLong()+"/cancel").with(mvc.as(seller))).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/promotions").param("placement","catalog")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/public/promotions").param("placement","home")).andExpect(jsonPath("$.length()").value(0));
        new TransactionTemplate(transactions).executeWithoutResult(t->products.findByProductId(p.getProductId()).orElseThrow().setListingStatus("PENDING"));
        mvc.perform(get("/api/public/promotions").param("placement","catalog")).andExpect(jsonPath("$.length()").value(0));
        new TransactionTemplate(transactions).executeWithoutResult(t->products.findByProductId(p.getProductId()).orElseThrow().setListingStatus("APPROVED"));
        new TransactionTemplate(transactions).executeWithoutResult(t->{var campaign=campaigns.findById(c.get("id").asLong()).orElseThrow();campaign.setStartsAt(Instant.now().minusSeconds(4*86400));campaign.setEndsAt(Instant.now().minusSeconds(86400));});
        mvc.perform(get("/api/public/promotions").param("placement","catalog")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/seller/promotions/"+c.get("id").asLong()+"/cancel").with(mvc.as(admin))).andExpect(status().isBadRequest());
    }
    @Test void unknownHistoricalRatesAndUnallocatedRefundsNeverInflateKnownNet()throws Exception {
        String admin=login("reviewer","ReviewOnly!2026");
        jdbc.update("update order_lines_snapshot set commission_rate_percent=null where fulfillment_id='DEMO-FULFILLMENT-001'");
        var unpriced=getReport(admin);assertEquals(unpriced.get("totals").get("fulfilledSales"),unpriced.get("totals").get("unpricedLegacySales"));assertEquals(0,unpriced.get("totals").get("knownNetEarnings").decimalValue().signum());
        jdbc.update("update order_lines_snapshot set commission_rate_percent=5 where fulfillment_id='DEMO-FULFILLMENT-001'");
        new TransactionTemplate(transactions).executeWithoutResult(t->{var o=orders.findByOrderId("DEMO-EARNINGS-001").orElseThrow();var refund=Refund.builder().refundId("TEST-REFUND-001").payment(o.getPayment()).amount(new BigDecimal("100")).currency("INR").status(RefundStatus.SUCCESS).build();o.getPayment().setRefund(refund);});
        var held=getReport(admin);assertEquals(0,new BigDecimal("100").compareTo(held.get("successfulRefunds").decimalValue()));assertEquals(0,held.get("totals").get("knownNetEarnings").decimalValue().signum());assertTrue(held.get("totals").get("refundHold").decimalValue().compareTo(new BigDecimal("100"))>=0);
        mvc.perform(get("/api/seller/promotions").with(mvc.as(admin))).andExpect(jsonPath("$.availableBudget").value(0));
        var r=request(products.findAll().get(0).getProductId(),LocalDate.now(ZoneOffset.UTC).toString());mvc.perform(post("/api/seller/promotions").with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(r))).andExpect(status().isBadRequest());
    }
    @Test void fullSuccessfulRefundReversesCommissionAndBuyerFeesEvenAfterShipment()throws Exception {
        String admin=login("reviewer","ReviewOnly!2026");
        new TransactionTemplate(transactions).executeWithoutResult(t->{var o=orders.findByOrderId("DEMO-EARNINGS-001").orElseThrow();o.getPayment().setRefund(Refund.builder().refundId("TEST-FULL-REFUND").payment(o.getPayment()).amount(o.getTotalAmount()).currency("INR").status(RefundStatus.SUCCESS).build());});
        var report=getReport(admin);
        assertEquals(0,report.get("platformFees").decimalValue().signum());
        assertEquals(0,report.get("totals").get("commission").decimalValue().signum());
        assertEquals(0,report.get("totals").get("knownNetEarnings").decimalValue().signum());
        assertEquals(report.get("totals").get("fulfilledSales"),report.get("totals").get("refundedFulfilledSales"));
        assertEquals(0,report.get("totals").get("refundHold").decimalValue().signum());
    }

    @Test @org.springframework.transaction.annotation.Transactional
    void verifiedLegacyFullRefundRetainsKnownShippedDeliveryAcrossFinanceMonthlyAndBudget() throws Exception {
        String admin=login("reviewer","ReviewOnly!2026");var seller=users.findByUsername("reviewer").orElseThrow();var baseline=getReport(admin);BigDecimal originalNet=baseline.get("totals").get("knownNetEarnings").decimalValue();
        var originalMonthly=finance.monthlyReport(seller.getUserId(),YearMonth.now(ZoneOffset.UTC),ZoneOffset.UTC);BigDecimal monthlyNet=(BigDecimal)((Map<?,?>)originalMonthly.get("totals")).get("knownNetEarnings");
        var o=new com.mercato.Entity.fulfillment.Order();o.setOrderId("LEGACY-REFUNDED-DELIVERY");o.setCustomerName("Synthetic buyer");o.setCustomerEmail("synthetic@test.invalid");o.setOrderStatus(OrderStatus.CREATED);o.setPaymentStatus(PaymentStatus.REFUNDED);o.setRecipientName("Synthetic buyer");o.setRecipientPhone("9999999999");o.setDeliveryAddressLine1("Synthetic address");o.setDeliveryCity("City");o.setDeliveryState("State");o.setDeliveryPincode("123456");o.setCurrency("INR");o.setSubtotal(new BigDecimal("200"));o.setCharges(BigDecimal.ZERO);o.setTotalAmount(new BigDecimal("200"));
        o.addOrderLine(OrderLine.builder().fulfillmentId("LEGACY-DELIVERY-F").orderLineNumber(1).productId(products.findAll().get(0).getProductId()).productName("Synthetic legacy sale").unitPrice(new BigDecimal("100")).orderedQty(2).acceptedQty(1).preparingQty(1).packedQty(1).shippedQty(1).lineTotal(new BigDecimal("200")).sellerName("Seller").sellerEmail(seller.getEmail()).commissionRatePercent(new BigDecimal("0.5")).sellerDeliveryCharge(new BigDecimal("50")).orderLineStatus(OrderLineStatus.FULFILLED).build());
        var p=new Payment();p.setAmount(o.getTotalAmount());p.setCurrency("INR");p.setPaymentMethod(PaymentMethod.UPI);p.setStatus(PaymentStatus.REFUNDED);p.setCapturedAmount(o.getTotalAmount());p.setCapturedAt(Instant.now());p.setCaptureVerifiedAt(p.getCapturedAt());p.setCaptureEvidenceId("synthetic-legacy-reader-proof");o.attachPayment(p);o.establishConfirmation(Instant.now().minusSeconds(7200));o.setOrderStatus(OrderStatus.FULFILLMENT_COMPLETE);p.setRefund(Refund.builder().refundId("LEGACY-DELIVERY-REFUND").payment(p).amount(o.getTotalAmount()).currency("INR").status(RefundStatus.SUCCESS).build());orders.saveAndFlush(o);
        var report=getReport(admin);assertEquals(0,new BigDecimal("25").compareTo(report.get("totals").get("sellerPaidDelivery").decimalValue()));assertEquals(0,originalNet.subtract(new BigDecimal("25")).compareTo(report.get("totals").get("knownNetEarnings").decimalValue()));
        var monthly=finance.monthlyReport(seller.getUserId(),YearMonth.now(ZoneOffset.UTC),ZoneOffset.UTC);assertEquals(0,new BigDecimal("25").compareTo((BigDecimal)((Map<?,?>)monthly.get("totals")).get("sellerPaidDelivery")));assertEquals(0,monthlyNet.subtract(new BigDecimal("25")).compareTo((BigDecimal)((Map<?,?>)monthly.get("totals")).get("knownNetEarnings")));
        mvc.perform(get("/api/seller/promotions").with(mvc.as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.availableBudget").value(originalNet.subtract(new BigDecimal("25")).doubleValue()));
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void cappedSnapshotIsSharedByFinanceAndPromotionFundingAndRefundReversal() throws Exception {
        String admin=login("reviewer","ReviewOnly!2026");
        jdbc.update("update order_lines_snapshot set unit_price=200000, shipped_qty=2, line_total=400000, commission_rate_percent=0.5, commission_cap_per_item=500, seller_delivery_charge=250 where fulfillment_id='DEMO-FULFILLMENT-001'");
        entities.clear();
        var report=getReport(admin);
        assertEquals(0,new BigDecimal("1000").compareTo(report.get("totals").get("commission").decimalValue()));
        assertEquals(0,new BigDecimal("398750").compareTo(report.get("totals").get("knownNetEarnings").decimalValue()));
        assertEquals(0,new BigDecimal("250").compareTo(report.get("totals").get("sellerPaidDelivery").decimalValue()));
        mvc.perform(get("/api/seller/promotions").with(mvc.as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.availableBudget").value(398750));
        jdbc.update("update order_lines_snapshot set shipped_qty=1 where fulfillment_id='DEMO-FULFILLMENT-001'"); entities.clear();
        assertEquals(0,new BigDecimal("125").compareTo(getReport(admin).get("totals").get("sellerPaidDelivery").decimalValue()));
        mvc.perform(get("/api/seller/promotions").with(mvc.as(admin))).andExpect(jsonPath("$.availableBudget").value(199375));
        var order=orders.findByOrderId("DEMO-EARNINGS-001").orElseThrow();
        order.getPayment().setRefund(Refund.builder().refundId("CAPPED-FULL-REFUND").payment(order.getPayment()).amount(order.getTotalAmount()).currency("INR").status(RefundStatus.SUCCESS).build());
        orders.flush();
        assertEquals(0,getReport(admin).get("totals").get("commission").decimalValue().signum());
        assertEquals(0,new BigDecimal("125").compareTo(getReport(admin).get("totals").get("sellerPaidDelivery").decimalValue()));
        mvc.perform(get("/api/seller/promotions").with(mvc.as(admin))).andExpect(jsonPath("$.availableBudget").value(0));
    }
}
