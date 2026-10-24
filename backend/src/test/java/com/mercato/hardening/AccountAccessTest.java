package com.mercato.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.*;
import com.mercato.Repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"marketplace.notices.initial-delay-ms=3600000","spring.datasource.url=jdbc:h2:mem:mercato-account-access;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("demo") @AutoConfigureMockMvc @DirtiesContext @Transactional
class AccountAccessTest {
    @Autowired MockMvc rawMvc; CookieBrowserHarness mvc;
    @org.junit.jupiter.api.BeforeEach void cookieBrowser(){mvc=new CookieBrowserHarness(rawMvc,json);} @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired RoleRepository roles; @Autowired ProductRepository products;
    @Autowired AccountAccessEventRepository events;
    String login(String name,String password)throws Exception {return mvc.login(name,password);}
    String admin()throws Exception { return login("reviewer","ReviewOnly!2026"); }
    EcommUser shopper(){return users.findByUsername("shopperreview").orElseThrow();}
    String access(EcommUser u){return "/api/admin/users/"+u.getUserId()+"/access";}
    String body(String action,long version)throws Exception {return json.writeValueAsString(Map.of("action",action,"reason","Synthetic regression decision", "expectedVersion",version));}
    void change(String token,EcommUser user,String action)throws Exception {mvc.perform(post(access(user)).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content(body(action,user.getAccessVersion()))).andExpect(status().isOk());}

    @Test void accessCommandsAreAdminOnlyProtectAdminsAndRequireReasonsAndVersions()throws Exception {
        String token=admin(); var user=shopper();
        mvc.perform(post(access(user)).contentType(MediaType.APPLICATION_JSON).content(body("DEACTIVATE",0))).andExpect(status().isUnauthorized());
        String shopper=login("shopperreview","ShopOnly!2026");
        mvc.perform(post(access(user)).with(mvc.as(shopper)).contentType(MediaType.APPLICATION_JSON).content(body("DEACTIVATE",0))).andExpect(status().isForbidden());
        mvc.perform(post(access(users.findByUsername("reviewer").orElseThrow())).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content(body("DEACTIVATE",0))).andExpect(status().isForbidden());
        for(var payload:List.of(Map.of("action","DEACTIVATE","reason","short","expectedVersion",0),Map.of("action","DEACTIVATE","reason","          ","expectedVersion",0),Map.of("action","DEACTIVATE","reason","Synthetic valid reason"),Map.of("action","OTHER","reason","Synthetic valid reason","expectedVersion",0)))
            mvc.perform(post(access(user)).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        mvc.perform(post(access(user)).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content(body("DEACTIVATE",99))).andExpect(status().isConflict());
        assertTrue(user.isEnabled()); assertEquals(0,events.count());
    }
    @Test void deactivationCannotBeUndoneByLoginAndOldSessionsNeverReturnAfterRestoration()throws Exception {
        String token=admin(),old=login("shopperreview","ShopOnly!2026"); var user=shopper();
        change(token,user,"DEACTIVATE");
        assertFalse(user.isEnabled()); assertFalse(user.isSelfDeactivated());
        mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","shopperreview","password","ShopOnly!2026")))).andExpect(status().isUnauthorized());
        assertFalse(user.isEnabled());
        mvc.perform(get("/api/auth/user").with(mvc.as(old))).andExpect(status().isUnauthorized());
        change(token,user,"REACTIVATE");
        mvc.perform(get("/api/auth/user").with(mvc.as(old))).andExpect(status().isUnauthorized());
        login("shopperreview","ShopOnly!2026"); assertEquals(2,user.getAccessVersion());
        assertTrue(user.getRoles().stream().anyMatch(r->r.getRoleName()==AppRole.ROLE_USER));
        mvc.perform(get("/api/admin/users/"+user.getUserId()+"/access-history").with(mvc.as(token))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].action").value("REACTIVATE")).andExpect(jsonPath("$.content[0].actorUsername").value("reviewer"));
    }
    @Test void voluntaryPauseRestoresOnlyWithCorrectPasswordAndRetainsHistory()throws Exception {
        String token=login("shopperreview","ShopOnly!2026"); var user=shopper();
        mvc.perform(patch("/api/account/deactivate").with(mvc.as(token))).andExpect(status().isOk());
        assertTrue(user.isSelfDeactivated()); assertFalse(user.isEnabled());
        mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","shopperreview","password","wrong-password")))).andExpect(status().isUnauthorized());
        assertFalse(user.isEnabled()); assertTrue(users.findByUserId(user.getUserId()).isPresent());
        login("shopperreview","ShopOnly!2026"); assertTrue(user.isEnabled()); assertFalse(user.isSelfDeactivated());
        mvc.perform(get("/api/auth/user").with(mvc.as(token))).andExpect(status().isUnauthorized());
    }
    @Test void adminCanBlockAVoluntarilyPausedAccountAndStaleCommandsCannotOverwriteDecisions()throws Exception {
        String admin=admin(),token=login("shopperreview","ShopOnly!2026"); var user=shopper();
        mvc.perform(patch("/api/account/deactivate").with(mvc.as(token))).andExpect(status().isOk());
        long pausedVersion=user.getAccessVersion();change(admin,user,"DEACTIVATE");assertFalse(user.isSelfDeactivated());
        mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","shopperreview","password","ShopOnly!2026")))).andExpect(status().isUnauthorized());
        mvc.perform(post(access(user)).with(mvc.as(admin)).contentType(MediaType.APPLICATION_JSON).content(body("REACTIVATE",pausedVersion))).andExpect(status().isConflict());
        assertFalse(user.isEnabled());assertEquals(1,events.count());
    }
    @Test void sellerSuspensionHidesListingsButPreservesShoppingAndExistingOrderManagement()throws Exception {
        String token=admin(); var seller=users.findByUsername("sellerreview").orElseThrow();
        seller.setSellerStatus(SellerStatus.APPROVED);seller.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());users.flush();
        var product=products.findAll().get(0);product.setSeller(seller);products.flush();assertTrue(product.isPurchasable());
        change(token,seller,"SUSPEND_SELLING");assertTrue(seller.isEnabled());assertTrue(seller.isSeller());assertFalse(product.isPurchasable());
        String session=login("sellerreview","SellerOnly!2026");
        mvc.perform(get("/api/public/products/"+product.getProductId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/products").param("keyword",product.getProductName())).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/seller/products").with(mvc.as(session))).andExpect(status().isOk());
        mvc.perform(get("/api/seller/open-fulfillment-orders").with(mvc.as(session))).andExpect(status().isOk());
        mvc.perform(get("/api/cart").with(mvc.as(session))).andExpect(status().isOk());
        mvc.perform(post("/api/seller/promotions").with(mvc.as(session)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("productId",product.getProductId(),"tier","DISCOVERY","durationDays",3,"startDate",java.time.LocalDate.now().toString(),"expectedFee",147,"requestId",UUID.randomUUID().toString(),"authorizeDeduction",true)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/seller/categories/"+product.getCategory().getCategoryId()+"/product").with(mvc.as(session)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("productName","Suspended listing","description","Synthetic regression product","primaryImageUrl","https://example.com/product.jpg","retailPrice",100,"weightKg",1,"lengthCm",10,"widthCm",10,"heightCm",10)))).andExpect(status().isForbidden());
        change(token,seller,"RESTORE_SELLING");assertTrue(product.isPurchasable());assertTrue(seller.isEnabled());
    }
    @Test void unapprovedOrUnverifiedAccountsCannotReceiveSellingAccessAndLegacyRoutesCannotBypassAudit()throws Exception {
        String token=admin();var user=shopper();
        mvc.perform(post(access(user)).with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content(body("RESTORE_SELLING",0))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/"+user.getUserId()+"/roles").with(mvc.as(token)).contentType(MediaType.APPLICATION_JSON).content("[\"ROLE_SELLER\"]")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/deactivate-account").param("userId",user.getUserId()).param("deleteNow","true").with(mvc.as(token))).andExpect(status().isBadRequest());
        assertTrue(users.findByUserId(user.getUserId()).isPresent());assertTrue(user.isEnabled());assertEquals(0,events.count());
    }
}
