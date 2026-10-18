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
    @Autowired MockMvc mvc;
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
        String guest = jwt.generateGuestTokenValue();
        String anotherGuest = jwt.generateGuestTokenValue();
        assertNotEquals(guest, anotherGuest, "Different shoppers must never share a JWT/cart, even within one second");
        mvc.perform(post("/api/cart/add").header("Authorization", "Bearer " + guest)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("cartItems", java.util.List.of(Map.of("productId", product, "quantity", 2))))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems[0].quantity").value(2));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + anotherGuest))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems.length()").value(0));

        var login = mvc.perform(post("/api/auth/signin").header("Authorization", "Bearer " + guest)
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"reviewer\",\"password\":\"ReviewOnly!2026\"}"))
            .andExpect(status().isOk()).andReturn();
        String userToken = json.readTree(login.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/user/addresses/add").header("Authorization", "Bearer " + userToken)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems.length()").value(1))
            .andExpect(jsonPath("$.cartItems[0].quantity").value(2));
        mvc.perform(put("/api/cart").header("Authorization", "Bearer " + userToken)
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

        mvc.perform(delete("/api/cart/remove/" + product).header("Authorization", "Bearer " + userToken)).andExpect(status().isOk());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cartItems.length()").value(0));
        mvc.perform(get("/api/public/products").param("pageSize", "1000000")).andExpect(status().isBadRequest());
    }
}
