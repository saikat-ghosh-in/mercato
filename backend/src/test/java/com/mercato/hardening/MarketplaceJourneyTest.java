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
import org.springframework.mail.SimpleMailMessage;
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
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired RoleRepository roles; @Autowired PasswordEncoder passwords;
    @Autowired CommissionSettingsRepository settings; @Autowired CommissionChangeRepository changes;
    @Autowired SellerNoticeRepository notices; @Autowired SellerNoticeMailer mailer; @Autowired CommissionService commissions;
    @MockitoBean JavaMailSender mail;
    static AtomicLong phones = new AtomicLong(8880000000L);

    @BeforeEach void resetRates() {
        notices.deleteAll(); changes.deleteAll();
        CommissionSettings baseline = settings.findById(1L).orElseThrow(); baseline.setRate(new BigDecimal("5.00")); settings.save(baseline);
        reset(mail);
    }
    EcommUser customer() {
        String name = "test" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(EcommUser.builder().username(name).email(name + "@mercato.invalid")
                .password(passwords.encode("TestingOnly!2026")).phoneNumber(String.valueOf(phones.incrementAndGet()))
                .emailVerified(true).enabled(true).roles(new HashSet<>(Set.of(roles.findByRoleName(AppRole.ROLE_USER).orElseThrow()))).build());
    }
    String login(String name, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", name, "password", password)))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    String admin() throws Exception { return login("reviewer", "ReviewOnly!2026"); }
    Map<String,Object> address(String name) { return Map.of("recipientName", name, "recipientPhone", "9999999999", "addressLine1", "12 Synthetic Lane", "city", "Bengaluru", "state", "Karnataka", "pincode", "560001", "country", "India"); }
    JsonNode addAddress(String token, String name) throws Exception {
        return json.readTree(mvc.perform(post("/api/user/addresses/add").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(address(name)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test void defaultsAreOwnedUniqueAndReassignedAfterDeletion() throws Exception {
        EcommUser user = customer(); String token = login(user.getUsername(), "TestingOnly!2026");
        JsonNode first = addAddress(token, "First"); JsonNode second = addAddress(token, "Second");
        assertTrue(first.get("defaultAddress").asBoolean()); assertFalse(second.get("defaultAddress").asBoolean());
        mvc.perform(patch("/api/user/addresses/" + second.get("addressId").asText() + "/default").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        var list = json.readTree(mvc.perform(get("/api/user/addresses").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertEquals(1, java.util.stream.StreamSupport.stream(list.spliterator(), false).filter(a -> a.get("defaultAddress").asBoolean()).count());
        assertEquals(second.get("addressId").asText(), java.util.stream.StreamSupport.stream(list.spliterator(), false).filter(a -> a.get("defaultAddress").asBoolean()).findFirst().orElseThrow().get("addressId").asText());
        EcommUser other = customer(); String otherToken = login(other.getUsername(), "TestingOnly!2026");
        mvc.perform(patch("/api/user/addresses/" + second.get("addressId").asText() + "/default").header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/user/addresses/" + second.get("addressId").asText()).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/user/addresses").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$[0].defaultAddress").value(true));
    }

    @Test void crossOriginAddressUpdatesPermitPatchPreflight() throws Exception {
        mvc.perform(options("/api/user/addresses/example/default").header("Origin", "http://127.0.0.1:5175")
                .header("Access-Control-Request-Method", "PATCH").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5175"));
    }

    @Test void deliveryQuotesPersistAndRejectUnknownMethodsAndClientPrices() throws Exception {
        var catalog = json.readTree(mvc.perform(get("/api/public/products")).andReturn().getResponse().getContentAsString());
        String product = java.util.stream.StreamSupport.stream(catalog.get("content").spliterator(), false).filter(p -> p.get("productName").asText().equals("Soft woven cushion")).findFirst().orElseThrow().get("productId").asText();
        var response = mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("cartItems", List.of(Map.of("productId", product, "quantity", 1)))))).andExpect(status().isOk()).andReturn();
        String guest = response.getResponse().getHeader("X-Guest-Token");
        JsonNode standard = json.readTree(response.getResponse().getContentAsString());
        assertEquals("STANDARD", standard.get("deliveryMethod").asText());
        assertEquals(new BigDecimal("50.00"), standard.get("deliveryOptions").get(0).get("price").decimalValue().setScale(2));
        mvc.perform(put("/api/cart/delivery-method").header("Authorization", "Bearer " + guest).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"EXPEDITED\",\"price\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryMethod").value("EXPEDITED")).andExpect(jsonPath("$.total").value(1060));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + guest)).andExpect(jsonPath("$.deliveryMethod").value("EXPEDITED"));
        mvc.perform(put("/api/cart/delivery-method").header("Authorization", "Bearer " + guest).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"FREE_INVENTED\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/delivery-method").header("Authorization", "Bearer " + guest).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"CONSOLIDATED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(961));
    }

    @Test void bulkyDeliveryCannotBeWaivedByClientAndRefreshesWithQuantity() throws Exception {
        var catalog=json.readTree(mvc.perform(get("/api/public/products").param("pageSize","50")).andReturn().getResponse().getContentAsString());
        String chair=java.util.stream.StreamSupport.stream(catalog.get("content").spliterator(),false).filter(p->p.get("productName").asText().equals("Linen lounge chair")).findFirst().orElseThrow().get("productId").asText();
        var response=mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("cartItems",List.of(Map.of("productId",chair,"quantity",2))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryOptions[0].price").value(1100)).andExpect(jsonPath("$.deliveryOptions[0].bulkyItemSurcharge").value(1100)).andReturn();
        String guest=response.getResponse().getHeader("X-Guest-Token");
        mvc.perform(put("/api/cart/delivery-method").header("Authorization","Bearer "+guest).contentType(MediaType.APPLICATION_JSON).content("{\"deliveryMethod\":\"EXPEDITED\",\"price\":0,\"bulkyItemSurcharge\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveryOptions[1].price").value(1199));
        mvc.perform(get("/api/cart").header("Authorization","Bearer "+guest)).andExpect(status().isOk()).andExpect(jsonPath("$.deliveryOptions[1].price").value(1199));
    }

    @Test void administratorsAreImmutableAndSellerRolesCannotBypassApproval() throws Exception {
        String token = admin(); String adminId = users.findByUsername("reviewer").orElseThrow().getUserId();
        mvc.perform(put("/api/admin/" + adminId + "/roles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("[\"ROLE_USER\"]")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/deactivate-account").param("userId", adminId).param("deleteNow", "true").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/reactivate-account").param("userId", adminId).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        EcommUser user = customer();
        mvc.perform(put("/api/admin/" + user.getUserId() + "/roles").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("[\"ROLE_USER\",\"ROLE_SELLER\"]")).andExpect(status().isBadRequest());
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
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"verificationConfirmed\":true}")).andExpect(status().isBadRequest());
        user.setEmailVerified(true); users.save(user); String applicant = login(name, "TestingOnly!2026");
        mvc.perform(get("/api/admin/seller-applications").header("Authorization", "Bearer " + applicant)).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/seller-application").header("Authorization", "Bearer " + applicant)).andExpect(jsonPath("$.pan").value("AAAAA9999A"));
        mvc.perform(get("/api/auth/user").header("Authorization", "Bearer " + applicant)).andExpect(jsonPath("$.pan").doesNotExist()).andExpect(jsonPath("$.gstin").doesNotExist());
        mvc.perform(get("/api/seller/products").header("Authorization", "Bearer " + applicant)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"verificationConfirmed\":true,\"note\":\"Synthetic test verification\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/seller/products").header("Authorization", "Bearer " + applicant)).andExpect(status().isOk());
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"verificationConfirmed\":true}")).andExpect(status().isBadRequest());
    }

    @Test void commissionCannotApplyBeforeEveryEmailAndFullNoticePeriodAndNoticeOwnershipIsEnforced() throws Exception {
        String token = admin();
        mvc.perform(post("/api/admin/commission").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}")).andExpect(status().isOk());
        assertEquals(new BigDecimal("5.00"), commissions.currentRate());
        CommissionChange change = changes.findAll().get(0); assertEquals("NOTIFYING", change.getStatus()); assertNull(change.getEffectiveAt());
        doThrow(new MailSendException("Simulated SMTP failure")).when(mail).send(any(SimpleMailMessage.class));
        SellerNotice notice = notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED").get(0);
        mailer.send(notice.getId());
        assertNull(changes.findById(change.getId()).orElseThrow().getEffectiveAt()); assertNull(notices.findById(notice.getId()).orElseThrow().getEmailSentAt());
        reset(mail); Instant beforeDelivery = Instant.now();
        for (SellerNotice n : notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED")) { n.setNextAttemptAt(Instant.now().minusSeconds(1)); notices.save(n); mailer.send(n.getId()); }
        change = changes.findById(change.getId()).orElseThrow(); assertEquals("SCHEDULED", change.getStatus()); assertFalse(change.getEffectiveAt().isBefore(beforeDelivery.plus(Duration.ofDays(60))));
        assertEquals(new BigDecimal("5.00"), commissions.currentRate());
        EcommUser other = customer(); String otherToken = login(other.getUsername(), "TestingOnly!2026");
        mvc.perform(post("/api/account/seller-notices/" + notice.getId() + "/read").header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/commission").header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON).content("{\"rate\":7,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 7%\"}")).andExpect(status().isForbidden());
        change.setEffectiveAt(Instant.now().minusSeconds(1)); changes.save(change);
        assertEquals(new BigDecimal("6.00"), commissions.currentRate());
        assertEquals("APPLIED", changes.findById(change.getId()).orElseThrow().getStatus());
    }

    @Test void onlyOneCommissionChangeCanBePendingAndCancellationLeavesCurrentRate() throws Exception {
        String token = admin(); String request = "{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}";
        mvc.perform(post("/api/admin/commission").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/commission").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        CommissionChange change = changes.findAll().get(0);
        mvc.perform(post("/api/admin/commission/" + change.getId() + "/cancel").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals(new BigDecimal("5.00"), commissions.currentRate());
        assertFalse(notices.findByChange_IdAndEventType(change.getId(), "CANCELLED").isEmpty());
        mvc.perform(get("/api/account/seller-notices").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$[0].event").value("CANCELLED"));
    }

    @Test void approvingAnotherSellerExtendsTheNoticePeriodInsteadOfShorteningIt() throws Exception {
        String token = admin();
        mvc.perform(post("/api/admin/commission").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}")).andExpect(status().isOk());
        CommissionChange change = changes.findAll().get(0);
        for (SellerNotice notice : notices.findByChange_IdAndEventType(change.getId(), "ANNOUNCED")) mailer.send(notice.getId());
        Instant original = changes.findById(change.getId()).orElseThrow().getEffectiveAt(); assertNotNull(original);
        EcommUser applicant = users.findByUsername("sellerreview").orElseThrow();
        mvc.perform(post("/api/admin/seller-applications/" + applicant.getUserId() + "/review").header("Authorization", "Bearer " + token)
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
        mvc.perform(post("/api/admin/commission").header("Authorization", "Bearer " + admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\":6,\"reason\":\"Improve marketplace seller support\",\"confirmation\":\"SCHEDULE 6%\"}")).andExpect(status().isOk());
        CommissionChange change = changes.findAll().get(0);
        assertTrue(notices.existsByUserIdAndChange_IdAndEventType(seller.getUserId(), change.getId(), "ANNOUNCED"));
        assertNull(change.getEffectiveAt());
    }
}
