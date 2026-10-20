package com.mercato.hardening;

import com.fasterxml.jackson.databind.*;
import com.mercato.Entity.*;
import com.mercato.Entity.fulfillment.*;
import com.mercato.Entity.fulfillment.payment.PaymentStatus;
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
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"marketplace.notices.initial-delay-ms=3600000","spring.datasource.url=jdbc:h2:mem:mercato-admin-activity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"}) @ActiveProfiles("demo") @AutoConfigureMockMvc @DirtiesContext @Transactional
class AdminUserActivityTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired RoleRepository roles; @Autowired ProductRepository products;
    @Autowired OrderRepository orders; @Autowired ListingRequestRepository requests;
    String login(String name,String password)throws Exception {return json.readTree(mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();}
    String admin()throws Exception{return login("reviewer","ReviewOnly!2026");}
    EcommUser shopper(){return users.findByUsername("shopperreview").orElseThrow();}
    EcommUser seller(){var user=users.findByUsername("sellerreview").orElseThrow();user.setSellerStatus(SellerStatus.APPROVED);user.getRoles().add(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow());users.flush();return user;}
    String activity(String id){return "/api/admin/users/"+id+"/activity";}
    JsonNode inspect(String token,String id,String section,String state)throws Exception{return json.readTree(mvc.perform(get(activity(id)).header("Authorization","Bearer "+token).param("section",section).param("status",state)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    String category(){return products.findAll().get(0).getCategory().getCategoryId();}
    Map<String,Object> body(String name){return new HashMap<>(Map.of("productName",name,"description","Synthetic activity regression listing","primaryImageUrl","https://example.com/test.jpg","retailPrice",199,"discountPercent",0,"weightKg",1,"lengthCm",20,"widthCm",15,"heightCm",10));}
    JsonNode create(String token,String category,Map<String,Object> body,boolean admin)throws Exception{return json.readTree(mvc.perform(post("/api/"+(admin?"admin":"seller")+"/categories/"+category+"/product").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    void review(String admin,String id,boolean approve)throws Exception {mvc.perform(post("/api/admin/products/"+id+"/review").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("approve",approve,"note",approve?"Verified synthetic submission":"Please correct the product information")))).andExpect(status().isOk());}
    void update(String token,String id,String name)throws Exception {mvc.perform(put("/api/seller/products/"+id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(name)))).andExpect(status().isOk()).andExpect(jsonPath("$.listingStatus").value("PENDING"));}

    @Test void activityIsAdminOnlyValidatesFiltersAndOmitsSellerDataForShoppers()throws Exception {
        String admin=admin(),shopperToken=login("shopperreview","ShopOnly!2026"),id=shopper().getUserId();
        mvc.perform(get(activity(id))).andExpect(status().isUnauthorized());
        mvc.perform(get(activity(id)).header("Authorization","Bearer "+shopperToken)).andExpect(status().isForbidden());
        String sellerToken=login("sellerreview","SellerOnly!2026");
        mvc.perform(get(activity(id)).header("Authorization","Bearer "+sellerToken)).andExpect(status().isForbidden());
        mvc.perform(get(activity("missing")).header("Authorization","Bearer "+admin)).andExpect(status().isNotFound());
        mvc.perform(get(activity(id)).header("Authorization","Bearer "+admin).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get(activity(id)).header("Authorization","Bearer "+admin).param("section","invented")).andExpect(status().isBadRequest());
        mvc.perform(get(activity(id)).header("Authorization","Bearer "+admin).param("status","invented")).andExpect(status().isBadRequest());
        mvc.perform(get(activity(id)).header("Authorization","Bearer "+admin).param("section","products")).andExpect(status().isBadRequest());
        var data=inspect(admin,id,"overview","ALL");assertFalse(data.get("sellerAccount").asBoolean());assertTrue(data.get("storeName").isNull());assertEquals(0,data.get("productCount").asInt());
        assertFalse(data.has("password"));assertFalse(data.has("pan"));assertFalse(data.has("guestToken"));
    }
    @Test void inspectionScopesCartWithoutMutatingItAndKeepsOtherAccountsSeparate()throws Exception {
        String admin=admin(),token=login("shopperreview","ShopOnly!2026"),id=shopper().getUserId();var product=products.findAll().get(0);
        product.setPhysicalQty(2); products.flush(); // The user's own two reserved units are still a valid bag.
        mvc.perform(post("/api/cart/add").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("cartItems",List.of(Map.of("productId",product.getProductId(),"quantity",2)))))).andExpect(status().isOk());
        var data=inspect(admin,id,"overview","ALL");assertEquals(2,data.get("cart").get("units").asInt());assertEquals(product.getProductId(),data.get("results").get("content").get(0).get("productId").asText());
        assertTrue(data.get("results").get("content").get(0).get("available").asBoolean());
        assertEquals(0,inspect(admin,users.findByUsername("reviewer").orElseThrow().getUserId(),"overview","ALL").get("results").get("totalElements").asInt());
        mvc.perform(get("/api/cart").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.cartItems[0].quantity").value(2));
    }
    @Test void ordersAreScopedAndPagedWithStableBoundedResults()throws Exception {
        String admin=admin(),id=shopper().getUserId();
        for(int i=0;i<12;i++){var order=new Order();order.setOrderId("ACTIVITY-"+i);order.setCustomerName("Test buyer");order.setCustomerEmail(shopper().getEmail());order.setOrderStatus(OrderStatus.CREATED);order.setPaymentStatus(PaymentStatus.INITIATED);order.setRecipientName("Test buyer");order.setRecipientPhone("9000000000");order.setDeliveryAddressLine1("Synthetic address");order.setDeliveryCity("Bengaluru");order.setDeliveryState("Karnataka");order.setDeliveryPincode("560001");order.setCurrency("INR");order.setSubtotal(BigDecimal.TEN);order.setCharges(BigDecimal.ZERO);order.setTotalAmount(BigDecimal.TEN);orders.save(order);}
        orders.flush();var first=inspect(admin,id,"orders","ALL");assertEquals(12,first.get("orderCount").asInt());assertEquals(10,first.get("results").get("content").size());assertEquals(2,first.get("results").get("totalPages").asInt());
        mvc.perform(get(activity(id)).header("Authorization","Bearer "+admin).param("section","orders").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.results.content.length()").value(2));
        assertEquals(0,inspect(admin,users.findByUsername("reviewer").orElseThrow().getUserId(),"orders","ALL").get("orderCount").asInt());
    }
    @Test void requestDecisionsResubmissionsAndWithdrawalsPreserveHistory()throws Exception {
        String admin=admin();var seller=seller();String token=login("sellerreview","SellerOnly!2026");String category=category();
        String id=create(token,category,body("Initial request"),false).get("productId").asText();
        review(admin,id,false);update(token,id,"Corrected request");review(admin,id,true);update(token,id,"Third submission");update(token,id,"Fourth submission");
        var all=inspect(admin,seller.getUserId(),"requests","ALL");assertEquals(4,all.get("requestCount").asInt());assertEquals(1,all.get("openRequestCount").asInt());
        assertEquals("Initial request",inspect(admin,seller.getUserId(),"requests","REJECTED").get("results").get("content").get(0).get("productName").asText());
        assertEquals("Corrected request",inspect(admin,seller.getUserId(),"requests","APPROVED").get("results").get("content").get(0).get("productName").asText());
        assertEquals(1,inspect(admin,seller.getUserId(),"requests","SUPERSEDED").get("results").get("totalElements").asInt());
        mvc.perform(delete("/api/seller/products/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        assertEquals(1,inspect(admin,seller.getUserId(),"requests","WITHDRAWN").get("results").get("totalElements").asInt());
        assertEquals(4,inspect(admin,seller.getUserId(),"requests","ALL").get("requestCount").asInt());
        assertEquals(0,inspect(admin,seller.getUserId(),"products","ALL").get("productCount").asInt());
    }
    @Test void administratorsCreateApprovedProductsDirectlyAndSellersCannotUseAdminCreation()throws Exception {
        String admin=admin(),category=category();var seller=seller();String token=login("sellerreview","SellerOnly!2026");
        var product=create(admin,category,body("Direct admin product"),true);assertEquals("APPROVED",product.get("listingStatus").asText());assertEquals(0,product.get("physicalQty").asInt());
        mvc.perform(get("/api/public/products/"+product.get("productId").asText())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/categories/"+category+"/product").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body("Forbidden creation")))).andExpect(status().isForbidden());
        assertEquals(0,requests.countBySellerId(users.findByUsername("reviewer").orElseThrow().getUserId()));
        var pending=create(token,category,body("Normal seller request"),false);assertEquals("PENDING",pending.get("listingStatus").asText());assertEquals(1,requests.countBySellerId(seller.getUserId()));
        mvc.perform(get("/api/public/products/"+pending.get("productId").asText())).andExpect(status().isNotFound());
    }
}
