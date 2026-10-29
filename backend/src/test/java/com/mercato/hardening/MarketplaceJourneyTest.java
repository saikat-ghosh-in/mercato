package com.mercato.hardening;

import com.fasterxml.jackson.databind.*;
import com.mercato.Entity.*;
import com.mercato.Repository.*;
import com.mercato.Service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import jakarta.mail.internet.MimeMessage;
import static com.mercato.mail.MimeContent.plainText;
import static com.mercato.mail.MimeContent.recipient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "marketplace.notices.initial-delay-ms=3600000")
@ActiveProfiles("demo") @AutoConfigureMockMvc @DirtiesContext
class MarketplaceJourneyTest {
    @Autowired MockMvc rawMvc; CookieBrowserHarness mvc;
    @org.junit.jupiter.api.BeforeEach void cookieBrowser(){mvc=new CookieBrowserHarness(rawMvc,json);} @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired RoleRepository roles; @Autowired PasswordEncoder passwords;
    @Autowired CommissionSettingsRepository settings; @Autowired CommissionChangeRepository changes;
    @Autowired SellerNoticeRepository notices; @Autowired SellerNoticeMailer mailer; @Autowired CommissionService commissions;
    @MockitoBean JavaMailSender mail;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired com.mercato.Jobs.DurableJobs jobs;
    @Autowired com.mercato.Jobs.JobStore jobStore;
    @Autowired com.mercato.Mail.MonthlyReportHandler monthlyHandler;
    @Autowired MarketplaceFinanceService finance;
    @Autowired EmailVerificationService verification;
    @Autowired com.mercato.Mail.AuthenticationMailGuard mailGuard;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired OrderRepository orders;
    Product reportProduct(EcommUser seller,String name,int stock){return products.saveAndFlush(Product.builder().productName(name).description("Synthetic monthly report isolation fixture").primaryImageUrl("/images/test.jpg").retailPrice(new BigDecimal("1000.00")).category(categories.findAll().get(0)).seller(seller).active(true).listingStatus("APPROVED").physicalQty(stock).build());}
    void reportSale(EcommUser seller,Product product,BigDecimal amount,YearMonth month){
        var order=new com.mercato.Entity.fulfillment.Order();String key=UUID.randomUUID().toString().substring(0,8);
        order.setOrderId("MAIL-ORDER-"+key);order.setCustomerName("Private customer");order.setCustomerEmail("private-customer@example.test");order.setOrderStatus(com.mercato.Entity.fulfillment.OrderStatus.CREATED);
        order.setRecipientName("Private customer");order.setRecipientPhone("9999999999");order.setDeliveryAddressLine1("Private address");order.setDeliveryCity("City");order.setDeliveryState("State");order.setDeliveryPincode("560001");order.setCurrency("INR");
        order.setSubtotal(amount);order.setCharges(new BigDecimal("12.00"));order.setPlatformFee(new BigDecimal("12.00"));order.setShippingCharge(BigDecimal.ZERO);order.setTotalAmount(amount.add(order.getCharges()));
        var payment=new com.mercato.Entity.fulfillment.payment.Payment();payment.setAmount(order.getTotalAmount());payment.setCurrency("INR");payment.setPaymentMethod(com.mercato.Entity.fulfillment.payment.PaymentMethod.UPI);payment.setStatus(com.mercato.Entity.fulfillment.payment.PaymentStatus.SUCCESS);payment.setCapturedAmount(order.getTotalAmount());payment.setCapturedAt(month.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant());payment.setGatewayName("test-only");payment.setCaptureVerifiedAt(payment.getCapturedAt());payment.setCaptureEvidenceId("test-reader-proof-"+key);order.attachPayment(payment);order.establishConfirmation(payment.getCapturedAt().minusSeconds(7200));
        order.setPaymentStatus(com.mercato.Entity.fulfillment.payment.PaymentStatus.SUCCESS);order.setOrderStatus(com.mercato.Entity.fulfillment.OrderStatus.FULFILLMENT_COMPLETE);order.setAllocationCompletedAt(Instant.now());
        order.addOrderLine(com.mercato.Entity.fulfillment.OrderLine.builder().fulfillmentId("MAIL-FULFILL-"+key).orderLineNumber(1).productId(product.getProductId()).productName(product.getProductName()).unitPrice(amount).orderedQty(1).shippedQty(1).lineTotal(amount).revenue(amount).sellerEmail(seller.getEmail()).sellerName(seller.getUsername()).commissionRatePercent(new BigDecimal("0.5")).commissionCapPerItem(new BigDecimal("500.00")).sellerDeliveryCharge(new BigDecimal("25.00")).orderLineStatus(com.mercato.Entity.fulfillment.OrderLineStatus.FULFILLED).build());
        orders.saveAndFlush(order);jdbc.update("UPDATE order_snapshot SET created_at=? WHERE id=?",java.sql.Timestamp.from(month.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant()),order.getId());
    }
    @Test void monthlyReportMoneyAndProductsAreIsolatedBetweenTwoSellers()throws Exception{
        var a=customer();a.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());a.setSellerStatus(SellerStatus.APPROVED);users.saveAndFlush(a);
        var b=customer();b.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());b.setSellerStatus(SellerStatus.APPROVED);users.saveAndFlush(b);
        var first=reportProduct(a,"Seller Alpha product",7);var second=reportProduct(b,"Seller Beta product",3);reportProduct(b,"Seller Beta second product",10);
        YearMonth month=YearMonth.now(ZoneId.of("Asia/Kolkata")).minusMonths(1);
        reportSale(a,first,new BigDecimal("1000.00"),month);reportSale(b,second,new BigDecimal("2000.00"),month);
        var alpha=finance.monthlyReport(a.getUserId(),month,ZoneId.of("Asia/Kolkata"));var beta=finance.monthlyReport(b.getUserId(),month,ZoneId.of("Asia/Kolkata"));
        assertEquals(new BigDecimal("1000.00"),((Map<?,?>)alpha.get("totals")).get("fulfilledSales"));assertEquals(new BigDecimal("2000.00"),((Map<?,?>)beta.get("totals")).get("fulfilledSales"));
        var adminReport=finance.monthlyReport(users.findByUsername("reviewer").orElseThrow().getUserId(),month,ZoneId.of("Asia/Kolkata"));
        assertEquals(new BigDecimal("3024.00"),adminReport.get("capturedPayments"));assertEquals(new BigDecimal("15.00"),((Map<?,?>)adminReport.get("totals")).get("commission"));
        new com.mercato.Mail.MonthlyReports(users,jobs,jdbc,true,"Asia/Kolkata").schedule();
        jdbc.update("UPDATE durable_jobs SET due_at=?",java.sql.Timestamp.from(Instant.now().plusSeconds(7200)));
        jdbc.update("UPDATE durable_jobs SET due_at=CURRENT_TIMESTAMP WHERE job_type='MONTHLY_REPORT' AND email_recipient IN (?,?)",a.getEmail(),b.getEmail());
        jobStore.claim("scoped-reports",2,Duration.ofSeconds(60)).forEach(record->new com.mercato.Jobs.JobProcessor(jobStore,List.of(monthlyHandler)).process(record));
        var capture=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(mail,times(2)).send(capture.capture());
        for(var message:capture.getAllValues()){
            String text=plainText(message);assertFalse(text.contains("Private customer"));assertFalse(text.contains("private-customer"));
            if(recipient(message).equals(a.getEmail())){assertTrue(text.contains("Fulfilled sales: ₹1000.00"));assertTrue(text.contains("Known net earnings: ₹970.00"));assertTrue(text.contains("Products currently listed: 1"));assertFalse(text.contains("2000.00"));}
            else{assertEquals(b.getEmail(),recipient(message));assertTrue(text.contains("Fulfilled sales: ₹2000.00"));assertTrue(text.contains("Known net earnings: ₹1965.00"));assertTrue(text.contains("Products currently listed: 2"));assertTrue(text.contains("(5 units or fewer): 1"));}
        }
    }
    static AtomicLong phones = new AtomicLong(8880000000L);

    @BeforeEach void resetRates() {
        notices.deleteAll(); changes.deleteAll();
        CommissionSettings baseline = settings.findById(1L).orElseThrow(); baseline.setRate(new BigDecimal("5.00")); settings.save(baseline);
        resetMail();
    }
    void resetMail(){ reset(mail);when(mail.createMimeMessage()).thenAnswer(call->new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()))); }
    EcommUser customer() {
        String name = "test" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(EcommUser.builder().username(name).email(name + "@mercato.invalid")
                .password(passwords.encode("TestingOnly!2026")).phoneNumber(String.valueOf(phones.incrementAndGet()))
                .emailVerified(true).enabled(true).roles(new HashSet<>(Set.of(roles.findByRoleName(AppRole.ROLE_USER).orElseThrow()))).build());
    }
    @Test void monthlyReportsAreDeduplicatedScopedAndSentOutsideDatabaseTransactions() throws Exception {
        var seller=customer();seller.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());seller.setSellerStatus(SellerStatus.APPROVED);users.saveAndFlush(seller);
        var applicant=customer();applicant.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());applicant.setSellerStatus(SellerStatus.PENDING);users.saveAndFlush(applicant);
        var reports=new com.mercato.Mail.MonthlyReports(users,jobs,jdbc,true,"Asia/Kolkata");
        reports.schedule();long first=jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs WHERE job_type='MONTHLY_REPORT'",Long.class);reports.schedule();
        assertEquals(first,jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs WHERE job_type='MONTHLY_REPORT'",Long.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs WHERE job_type='MONTHLY_REPORT' AND email_recipient=?",Integer.class,seller.getEmail()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM durable_jobs WHERE job_type='MONTHLY_REPORT' AND email_recipient=?",Integer.class,applicant.getEmail()));
        jdbc.update("UPDATE durable_jobs SET due_at=?",java.sql.Timestamp.from(Instant.now().plusSeconds(7200)));
        jdbc.update("UPDATE durable_jobs SET due_at=CURRENT_TIMESTAMP WHERE job_type='MONTHLY_REPORT' AND email_recipient=?",seller.getEmail());
        doAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return null;}).when(mail).send(any(MimeMessage.class));
        var record=jobStore.claim("report-test",1,Duration.ofSeconds(60)).get(0);assertEquals("MONTHLY_REPORT",record.type());
        new com.mercato.Jobs.JobProcessor(jobStore,List.of(monthlyHandler)).process(record);
        assertEquals(com.mercato.Jobs.JobStatus.SUCCEEDED,jobStore.get(record.id()).status());
        var capture=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(mail).send(capture.capture());
        assertArrayEquals(new String[]{seller.getEmail()},new String[]{recipient(capture.getValue())});
        assertTrue(plainText(capture.getValue()).contains("Your monthly seller report"));
        assertFalse(plainText(capture.getValue()).contains("Verified captures"));
        mvc.perform(get("/api/admin/operations/emails").with(mvc.as(login(seller.getUsername(),"TestingOnly!2026")))).andExpect(status().isForbidden());
        var response=mvc.perform(get("/api/admin/operations/emails").with(mvc.as(admin()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].purpose").isString()).andExpect(jsonPath("$.items[0].state").isString())
            .andExpect(jsonPath("$.summary.failed").isNumber()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("business_key"));assertFalse(response.contains("payload"));assertTrue(response.contains("ACCEPTED"));
    }
    @Test void supersededAndUsedVerificationTokensAreNeverMailed() {
        var user=customer();user.setEmailVerified(false);users.saveAndFlush(user);
        verification.createAndSendVerificationToken(user);
        UUID first=jdbc.queryForObject("SELECT id FROM durable_jobs WHERE email_recipient=? AND email_purpose='VERIFICATION'",UUID.class,user.getEmail());
        assertTrue(mailGuard.current(jobStore.get(first)));
        verification.resendVerificationEmail(user.getEmail());
        assertFalse(mailGuard.current(jobStore.get(first)));
        var latest=jdbc.queryForObject("SELECT id FROM durable_jobs WHERE email_recipient=? AND email_purpose='VERIFICATION' AND id<>?",UUID.class,user.getEmail(),first);
        assertTrue(mailGuard.current(jobStore.get(latest)));
        jdbc.update("UPDATE email_verification_tokens SET verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.getId());
        assertFalse(mailGuard.current(jobStore.get(latest)));
    }
    String login(String name,String password)throws Exception {return mvc.login(name,password);}
    String admin() throws Exception { return login("reviewer", "ReviewOnly!2026"); }
    Map<String,Object> address(String name) { return Map.of("recipientName", name, "recipientPhone", "9999999999", "addressLine1", "12 Synthetic Lane", "city", "Bengaluru", "state", "Karnataka", "pincode", "560001", "country", "India"); }
    JsonNode addAddress(String token, String name) throws Exception {
        return json.readTree(mvc.perform(post("/api/user/addresses/add").with(mvc.as(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(address(name)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test void defaultsAreOwnedUniqueAndReassignedAfterDeletion() throws Exception {
        EcommUser user = customer(); String token = login(user.getUsername(), "TestingOnly!2026");
        JsonNode first = addAddress(token, "First"); JsonNode second = addAddress(token, "Second");
        assertTrue(first.get("defaultAddress").asBoolean()); assertFalse(second.get("defaultAddress").asBoolean());
        mvc.perform(patch("/api/user/addresses/" + second.get("addressId").asText() + "/default").with(mvc.as(token))).andExpect(status().isOk());
        var list = json.readTree(mvc.perform(get("/api/user/addresses").with(mvc.as(token))).andReturn().getResponse().getContentAsString());
        assertEquals(1, java.util.stream.StreamSupport.stream(list.spliterator(), false).filter(a -> a.get("defaultAddress").asBoolean()).count());
        assertEquals(second.get("addressId").asText(), java.util.stream.StreamSupport.stream(list.spliterator(), false).filter(a -> a.get("defaultAddress").asBoolean()).findFirst().orElseThrow().get("addressId").asText());
        EcommUser other = customer(); String otherToken = login(other.getUsername(), "TestingOnly!2026");
        mvc.perform(patch("/api/user/addresses/" + second.get("addressId").asText() + "/default").with(mvc.as(otherToken))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/user/addresses/" + second.get("addressId").asText()).with(mvc.as(token))).andExpect(status().isOk());
        mvc.perform(get("/api/user/addresses").with(mvc.as(token))).andExpect(jsonPath("$[0].defaultAddress").value(true));
    }

    @Test void crossOriginAddressUpdatesDoNotExposePatchPreflight() throws Exception {
        mvc.perform(options("/api/user/addresses/example/default").header("Origin", "http://127.0.0.1:5175")
                .header("Access-Control-Request-Method", "PATCH").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Methods"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void deliveryQuotesPersistAndRejectUnknownMethodsAndClientPrices() throws Exception {
        var catalog = json.readTree(mvc.perform(get("/api/public/products")).andReturn().getResponse().getContentAsString());
        String product = java.util.stream.StreamSupport.stream(catalog.get("content").spliterator(), false).filter(p -> p.get("productName").asText().equals("Soft woven cushion")).findFirst().orElseThrow().get("productId").asText();
        var response = mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("cartItems", List.of(Map.of("productId", product, "quantity", 1)))))).andExpect(status().isOk()).andReturn();
        String guest = mvc.defaultAnonymous();
        JsonNode standard = json.readTree(response.getResponse().getContentAsString());
        assertEquals("STANDARD", standard.get("deliveryMethod").asText());
        assertEquals(new BigDecimal("50.00"), standard.get("deliveryOptions").get(0).get("price").decimalValue().setScale(2));
        mvc.perform(put("/api/cart/delivery-method").with(mvc.as(guest)).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"EXPEDITED\",\"price\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryMethod").value("EXPEDITED")).andExpect(jsonPath("$.total").value(1010));
        mvc.perform(get("/api/cart").with(mvc.as(guest))).andExpect(jsonPath("$.deliveryMethod").value("EXPEDITED"));
        mvc.perform(put("/api/cart/delivery-method").with(mvc.as(guest)).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"FREE_INVENTED\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/delivery-method").with(mvc.as(guest)).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"CONSOLIDATED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(961));
    }

    @Test void bulkyDeliveryCannotBeWaivedByClientAndRefreshesWithQuantity() throws Exception {
        var catalog=json.readTree(mvc.perform(get("/api/public/products").param("pageSize","50")).andReturn().getResponse().getContentAsString());
        String chair=java.util.stream.StreamSupport.stream(catalog.get("content").spliterator(),false).filter(p->p.get("productName").asText().equals("Linen lounge chair")).findFirst().orElseThrow().get("productId").asText();
        var response=mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("cartItems",List.of(Map.of("productId",chair,"quantity",2))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryOptions[0].price").value(250)).andExpect(jsonPath("$.deliveryOptions[0].bulkyItemSurcharge").value(250)).andReturn();
        String guest=mvc.defaultAnonymous();
        mvc.perform(put("/api/cart/delivery-method").with(mvc.as(guest)).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"EXPEDITED\",\"price\":0,\"bulkyItemSurcharge\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryOptions[1].price").value(299));
        mvc.perform(get("/api/cart").with(mvc.as(guest))).andExpect(status().isOk()).andExpect(jsonPath("$.deliveryOptions[1].price").value(299));
    }

    @Test @org.springframework.transaction.annotation.Transactional
    void sellerCoverageComesOnlyFromTheListingAndRefreshesServerQuotes() throws Exception {
        var catalog=json.readTree(mvc.perform(get("/api/public/products").param("pageSize","50")).andReturn().getResponse().getContentAsString());
        String chair=java.util.stream.StreamSupport.stream(catalog.get("content").spliterator(),false).filter(p->p.get("productName").asText().equals("Linen lounge chair")).findFirst().orElseThrow().get("productId").asText();
        var response=mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("cartItems",List.of(Map.of("productId",chair,"quantity",1,"sellerPaysShipping",true))))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryOptions[0].price").value(250)).andReturn();
        String guest=mvc.defaultAnonymous();
        jdbc.update("update products set seller_pays_shipping=true where product_id=?",chair); entities.clear();
        mvc.perform(get("/api/public/products/"+chair)).andExpect(jsonPath("$.sellerPaysShipping").value(true));
        mvc.perform(get("/api/cart").with(mvc.as(guest))).andExpect(jsonPath("$.deliveryOptions[0].price").value(0))
            .andExpect(jsonPath("$.deliveryOptions[0].sellerPaidStandard").value(true)).andExpect(jsonPath("$.deliveryOptions[1].price").value(49));
        mvc.perform(put("/api/cart/delivery-method").with(mvc.as(guest)).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"EXPEDITED\",\"price\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11110.15));
        jdbc.update("update products set seller_pays_shipping=false where product_id=?",chair); entities.clear();
        mvc.perform(get("/api/cart").with(mvc.as(guest))).andExpect(jsonPath("$.deliveryOptions[0].price").value(250)).andExpect(jsonPath("$.deliveryOptions[1].price").value(299));
    }

    @Test void administratorsAreImmutableAndSellerRolesCannotBypassApproval() throws Exception {
        String token = admin(); String adminId = users.findByUsername("reviewer").orElseThrow().getUserId();
        mvc.perform(put("/api/admin/" + adminId + "/roles").with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("[\"ROLE_USER\"]")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/deactivate-account").param("userId", adminId).param("deleteNow", "true").with(mvc.as(token))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/reactivate-account").param("userId", adminId).with(mvc.as(token))).andExpect(status().isForbidden());
        EcommUser user = customer();
        mvc.perform(put("/api/admin/" + user.getUserId() + "/roles").with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("[\"ROLE_USER\",\"ROLE_SELLER\"]")).andExpect(status().isBadRequest());
    }

    Map<String,Object> registration(String name) {
        Map<String,Object> body = new HashMap<>();
        body.put("username", name); body.put("email", name + "@mercato.invalid"); body.put("password", "TestingOnly!2026"); body.put("confirmPassword", "TestingOnly!2026"); body.put("phoneNumber", String.valueOf(phones.incrementAndGet()));
        return body;
    }
    Map<String,Object> business() {
        Map<String,Object> app = new HashMap<>();
        app.put("storeName", "Synthetic store"); app.put("legalBusinessName", "Synthetic Legal Business"); app.put("businessType", "SOLE_PROPRIETOR"); app.put("pan", "AAAAA9999A"); app.put("gstin", "29AAAAA9999A1Z5"); app.put("taxRegistrationType", "GST_REGISTERED"); app.put("pickupAddress", "12 Synthetic Pickup Lane"); app.put("pickupState", "Karnataka"); app.put("pickupPincode", "560001"); app.put("supportEmail", "synthetic@mercato.invalid"); app.put("productCategories", "Home & Living"); app.put("policyVersion", "2026-10-draft"); app.put("policiesAcknowledged", true); return app;
    }
    @Test void sellerRegistrationIsPendingAndApprovalRequiresVerifiedEmailAndAttestation() throws Exception {
        String name = "seller" + UUID.randomUUID().toString().substring(0,8);
        Map<String,Object> body = registration(name); body.put("accountType", "SELLER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        body.put("sellerApplication", business()); body.put("confirmPassword", "WrongPassword");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        body.put("confirmPassword", "TestingOnly!2026");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
        EcommUser user = users.findByUsername(name).orElseThrow(); assertEquals(SellerStatus.PENDING, user.getSellerStatus()); assertFalse(user.isSeller());
        String token = admin(); String path = "/api/admin/seller-applications/" + user.getUserId() + "/review";
        mvc.perform(post(path).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"verificationConfirmed\":true}")).andExpect(status().isBadRequest());
        user.setEmailVerified(true); users.save(user); String applicant = login(name, "TestingOnly!2026");
        mvc.perform(get("/api/admin/seller-applications").with(mvc.as(applicant))).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/seller-application").with(mvc.as(applicant))).andExpect(jsonPath("$.pan").value("AAAAA9999A"));
        mvc.perform(get("/api/auth/user").with(mvc.as(applicant))).andExpect(jsonPath("$.pan").doesNotExist()).andExpect(jsonPath("$.gstin").doesNotExist());
        mvc.perform(get("/api/seller/products").with(mvc.as(applicant))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"verificationConfirmed\":true,\"note\":\"Synthetic test verification\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/seller/products").with(mvc.as(applicant))).andExpect(status().isUnauthorized());
        applicant=login(name,"TestingOnly!2026");
        mvc.perform(get("/api/seller/products").with(mvc.as(applicant))).andExpect(status().isOk());
        mvc.perform(post(path).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"verificationConfirmed\":true}")).andExpect(status().isBadRequest());
    }

    @Test void commissionCannotApplyBeforeEveryEmailAndFullNoticePeriodAndNoticeOwnershipIsEnforced() throws Exception {
        String token = admin();
        mvc.perform(post("/api/admin/commission").with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}")).andExpect(status().isOk());
        assertEquals(new BigDecimal("5.00"), commissions.currentRate());
        CommissionChange change = changes.findAll().get(0); assertEquals("NOTIFYING", change.getStatus()); assertNull(change.getEffectiveAt());
        doThrow(new MailSendException("Simulated SMTP failure")).when(mail).send(any(MimeMessage.class));
        SellerNotice notice = notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED").get(0);
        mailer.send(notice.getId());
        assertNull(changes.findById(change.getId()).orElseThrow().getEffectiveAt()); assertNull(notices.findById(notice.getId()).orElseThrow().getEmailSentAt());
        resetMail(); Instant beforeDelivery = Instant.now();
        for (SellerNotice n : notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED")) { n.setNextAttemptAt(Instant.now().minusSeconds(1)); notices.save(n); mailer.send(n.getId()); }
        change = changes.findById(change.getId()).orElseThrow(); assertEquals("SCHEDULED", change.getStatus()); assertFalse(change.getEffectiveAt().isBefore(beforeDelivery.plus(Duration.ofDays(60))));
        assertEquals(new BigDecimal("5.00"), commissions.currentRate());
        EcommUser other = customer(); String otherToken = login(other.getUsername(), "TestingOnly!2026");
        mvc.perform(post("/api/account/seller-notices/" + notice.getId() + "/read").with(mvc.as(otherToken))).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/commission").with(mvc.as(otherToken)).contentType(MediaType.APPLICATION_JSON).content("{\"rate\":7,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 7%\"}")).andExpect(status().isForbidden());
        change.setEffectiveAt(Instant.now().minusSeconds(1)); changes.save(change);
        assertEquals(new BigDecimal("6.00"), commissions.currentRate());
        assertEquals("APPLIED", changes.findById(change.getId()).orElseThrow().getStatus());
    }

    @Test void onlyOneCommissionChangeCanBePendingAndCancellationLeavesCurrentRate() throws Exception {
        String token = admin(); String request = "{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}";
        mvc.perform(post("/api/admin/commission").with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/commission").with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        CommissionChange change = changes.findAll().get(0);
        mvc.perform(post("/api/admin/commission/" + change.getId() + "/cancel").with(mvc.as(token))).andExpect(status().isOk());
        assertEquals(new BigDecimal("5.00"), commissions.currentRate());
        assertFalse(notices.findByChange_IdAndEventType(change.getId(), "CANCELLED").isEmpty());
        mvc.perform(get("/api/account/seller-notices").with(mvc.as(token))).andExpect(jsonPath("$[0].event").value("CANCELLED"));
    }

    @Test void approvingAnotherSellerExtendsTheNoticePeriodInsteadOfShorteningIt() throws Exception {
        String token = admin();
        mvc.perform(post("/api/admin/commission").with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}")).andExpect(status().isOk());
        CommissionChange change = changes.findAll().get(0);
        for (SellerNotice notice : notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED")) mailer.send(notice.getId());
        Instant original = changes.findById(change.getId()).orElseThrow().getEffectiveAt(); assertNotNull(original);
        EcommUser applicant = users.findByUsername("sellerreview").orElseThrow();
        mvc.perform(post("/api/admin/seller-applications/" + applicant.getUserId() + "/review").with(mvc.as(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"verificationConfirmed\":true,\"note\":\"Synthetic verification for notice test\"}")).andExpect(status().isOk());
        change = changes.findById(change.getId()).orElseThrow(); assertEquals("NOTIFYING", change.getStatus()); assertNull(change.getEffectiveAt());
        SellerNotice fresh = notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED").stream().filter(n -> n.getUserId().equals(applicant.getUserId())).findFirst().orElseThrow();
        mailer.send(fresh.getId());
        assertFalse(changes.findById(change.getId()).orElseThrow().getEffectiveAt().isBefore(original));
        assertEquals(new BigDecimal("5.00"), commissions.currentRate());
    }

    @Test void inactiveApprovedSellersStillReceiveFullNoticeBeforeAChange() throws Exception {
        EcommUser seller = customer(); seller.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());
        seller.setSellerStatus(SellerStatus.APPROVED); seller.setEnabled(false); seller.setAccountLocked(true); users.save(seller);
        mvc.perform(post("/api/admin/commission").with(mvc.as(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}")).andExpect(status().isOk());
        CommissionChange change = changes.findAll().get(0);
        assertTrue(notices.existsByUserIdAndChange_IdAndEventType(seller.getUserId(), change.getId(), "ANNOUNCED"));
        assertNull(change.getEffectiveAt());
    }
}
