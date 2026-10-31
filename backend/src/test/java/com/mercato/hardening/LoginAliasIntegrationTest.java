package com.mercato.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.*;
import com.mercato.Repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc @DirtiesContext
class LoginAliasIntegrationTest {
    @Autowired MockMvc rawMvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired PasswordEncoder encoder;
    @Test void emailLoginAndRegistrationShareUnambiguousCaseInsensitiveAliases()throws Exception{
        var mvc=new CookieBrowserHarness(rawMvc,json);
        var role=roles.findByRoleName(AppRole.ROLE_USER).orElseThrow();
        users.saveAndFlush(EcommUser.builder().username("photo-user").email("photo@example.test").password(encoder.encode("PhotoOnly!123")).emailVerified(true).enabled(true).profileImageUrl("placeholder").roles(new HashSet<>(Set.of(role))).build());
        String shopper=mvc.login(" PHOTO@EXAMPLE.TEST ","PhotoOnly!123");
        assertTrue(users.existsByEmailIgnoreCase("PHOTO@EXAMPLE.TEST"));
        mvc.perform(post("/api/auth/register").with(mvc.as(shopper)).contentType("application/json").content(json.writeValueAsString(Map.of("username","different-user","email","PHOTO@EXAMPLE.TEST","password","PhotoOnly!123","confirmPassword","PhotoOnly!123","phoneNumber","9876543210")))).andExpect(status().isConflict());
        users.saveAndFlush(EcommUser.builder().username("unverified-alias").email("PHOTO@EXAMPLE.TEST").password(encoder.encode("PhotoOnly!123")).emailVerified(false).enabled(true).roles(new HashSet<>(Set.of(role))).build());
        assertEquals(List.of("photo-user"),users.findLoginUsernames("PHOTO@EXAMPLE.TEST"));

    }
}
