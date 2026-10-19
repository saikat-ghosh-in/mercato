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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@DirtiesContext
class RichProductFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    String login(String name, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", name, "password", password))))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void richDetailsPersistValidatePreserveLegacyFieldsAndEnforceOwnership() throws Exception {
        String token = login("reviewer", "ReviewOnly!2026");
        var catalog = mvc.perform(get("/api/public/products")).andExpect(status().isOk()).andReturn();
        var product = json.readTree(catalog.getResponse().getContentAsString()).get("content").get(0);
        String id = product.get("productId").asText();
        Map<String,Object> body = new HashMap<>();
        body.put("productName", product.get("productName").asText());
        body.put("description", product.get("description").asText());
        body.put("primaryImageUrl", product.get("primaryImageUrl").asText());
        body.put("retailPrice", product.get("retailPrice").decimalValue());
        body.put("discountPercent", product.get("discountPercent").decimalValue());
        body.put("physicalQty", 25);
        body.put("active", true);
        body.put("brand", "Review Brand");
        body.put("highlights", List.of("A verified highlight", "Another useful detail"));
        body.put("specifications", List.of(Map.of("label","Material","value","Cotton"), Map.of("label","Care","value","Hand wash")));
        body.put("care", "Wash gently.");
        mvc.perform(put("/api/seller/products/" + id).header("Authorization","Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/public/products/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.brand").value("Review Brand"))
            .andExpect(jsonPath("$.highlights[1]").value("Another useful detail"))
            .andExpect(jsonPath("$.specifications[0].label").value("Material"))
            .andExpect(jsonPath("$.specifications[1].value").value("Hand wash"));

        body.remove("brand"); body.remove("highlights"); body.remove("specifications"); body.remove("care");
        body.remove("physicalQty"); // Catalogue updates must not overwrite inventory.
        mvc.perform(put("/api/seller/products/" + id).header("Authorization","Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.brand").value("Review Brand"))
            .andExpect(jsonPath("$.highlights.length()").value(2))
            .andExpect(jsonPath("$.physicalQty").value(25));
        body.put("specifications", Arrays.asList((Object)null));
        mvc.perform(put("/api/seller/products/" + id).header("Authorization","Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        body.put("specifications", List.of(Map.of("label","","value","Invalid")));
        mvc.perform(put("/api/seller/products/" + id).header("Authorization","Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        body.put("specifications", List.of()); body.put("highlights", List.of()); body.put("care", "");
        mvc.perform(put("/api/seller/products/" + id).header("Authorization","Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.highlights.length()").value(0))
            .andExpect(jsonPath("$.specifications.length()").value(0)).andExpect(jsonPath("$.care").value(""));
        mvc.perform(put("/api/seller/products/" + id).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());

        users.save(EcommUser.builder().username("otherSeller").email("other@mercato.invalid")
            .password(encoder.encode("OtherReview!2026")).phoneNumber("9999999998")
            .enabled(true).emailVerified(true).accountLocked(false)
            .roles(Set.of(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow())).build());
        String other = login("otherSeller", "OtherReview!2026");
        mvc.perform(put("/api/seller/products/" + id).header("Authorization","Bearer " + other)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/products").header("Authorization","Bearer " + other)).andExpect(status().isForbidden());
    }
}
