package com.mercato.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.cart.CartReservation;
import com.mercato.Repository.CartReservationRepository;
import com.mercato.Service.CartReservationService;
import com.mercato.Security.jwt.JwtUtils;
import com.mercato.ExceptionHandler.InsufficientInventoryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@DirtiesContext
class ApplicationFlowTest {
    @Autowired MockMvc rawMvc; CookieBrowserHarness mvc;
    @org.junit.jupiter.api.BeforeEach void cookieBrowser(){mvc=new CookieBrowserHarness(rawMvc,json);}
    @Autowired ObjectMapper json;
    @Autowired CartReservationRepository reservations;
    @Autowired CartReservationService reservationService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtUtils jwt;

    @Test
    void catalogGuestIsolationBagAndLoginMergeUseRealDatabaseAndSecurity() throws Exception {
        var catalog = mvc.perform(get("/api/public/products").param("pageSize", "12"))
            .andExpect(status().isOk()).andExpect(header().doesNotExist("X-Guest-Token"))
            .andExpect(jsonPath("$.totalElements").value(12)).andReturn();
        String product = json.readTree(catalog.getResponse().getContentAsString()).get("content").get(0).get("productId").asText();
        String guest = mvc.anonymous();
        String anotherGuest = mvc.anonymous();
        assertNotEquals(guest, anotherGuest, "Different shoppers must never share a session/cart, even within one second");
        mvc.perform(post("/api/cart/add").with(mvc.as(guest))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("cartItems", java.util.List.of(Map.of("productId", product, "quantity", 2))))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems[0].quantity").value(2));
        mvc.perform(get("/api/cart").with(mvc.as(anotherGuest)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems.length()").value(0));

        String userToken = mvc.login(guest,"reviewer","ReviewOnly!2026");
        mvc.perform(post("/api/user/addresses/add").with(mvc.as(userToken))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cart").with(mvc.as(userToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems.length()").value(1))
            .andExpect(jsonPath("$.cartItems[0].quantity").value(2));
        mvc.perform(put("/api/cart").with(mvc.as(userToken))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("productId", product, "quantity", 3))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems[0].quantity").value(3));

        var expiredItem = new TransactionTemplate(transactions).execute(tx -> {
            CartReservation hold = reservations.findAll().get(0);
            hold.setExpiresAt(Instant.now().minusSeconds(1));
            reservations.saveAndFlush(hold);
            hold.getCartItem().getProduct().getProductId();
            return hold.getCartItem();
        });
        assertThrows(InsufficientInventoryException.class, () -> reservationService.validateHeld(expiredItem));

        mvc.perform(delete("/api/cart/remove/" + product).with(mvc.as(userToken))).andExpect(status().isOk());
        mvc.perform(get("/api/cart").with(mvc.as(userToken)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems.length()").value(0));
        mvc.perform(get("/api/public/products").param("pageSize", "1000000")).andExpect(status().isBadRequest());
    }
}
