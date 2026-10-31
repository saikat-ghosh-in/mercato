package com.mercato.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercato.Entity.*;
import com.mercato.Repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("demo") @AutoConfigureMockMvc @DirtiesContext
class ProfilePhotoTest {
    @TempDir static Path images;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("file.upload.dir",()->images.toString());p.add("file.base.url",()->"http://127.0.0.1/images");}
    @Autowired MockMvc rawMvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired PasswordEncoder encoder;
    @Test void anyAuthenticatedShopperCanUploadAndSaveTheirOwnProfilePhoto()throws Exception{
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1sAAAAASUVORK5CYII=");
        var file=new MockMultipartFile("file","photo.png","image/png",png);
        var mvc=new CookieBrowserHarness(rawMvc,json);
        mvc.perform(multipart("/api/user/profile-image").file(file)).andExpect(status().isUnauthorized());
        var role=roles.findByRoleName(AppRole.ROLE_USER).orElseThrow();
        users.saveAndFlush(EcommUser.builder().username("photo-user").email("photo@example.test").password(encoder.encode("PhotoOnly!123")).emailVerified(true).enabled(true).profileImageUrl("placeholder").roles(new HashSet<>(Set.of(role))).build());
        String shopper=mvc.login(" PHOTO@EXAMPLE.TEST ","PhotoOnly!123");
        assertTrue(users.existsByEmailIgnoreCase("PHOTO@EXAMPLE.TEST"));
        mvc.perform(post("/api/auth/register").with(mvc.as(shopper)).contentType("application/json").content(json.writeValueAsString(Map.of("username","different-user","email","PHOTO@EXAMPLE.TEST","password","PhotoOnly!123","confirmPassword","PhotoOnly!123","phoneNumber","9876543210")))).andExpect(status().isConflict());
        users.saveAndFlush(EcommUser.builder().username("unverified-alias").email("PHOTO@EXAMPLE.TEST").password(encoder.encode("PhotoOnly!123")).emailVerified(false).enabled(true).roles(new HashSet<>(Set.of(role))).build());
        assertEquals(List.of("photo-user"),users.findLoginUsernames("PHOTO@EXAMPLE.TEST"));
        var response=mvc.perform(multipart("/api/user/profile-image").file(file).with(mvc.as(shopper))).andExpect(status().isOk()).andReturn();
        String url=json.readTree(response.getResponse().getContentAsString()).path("imageUrl").asText();assertTrue(url.startsWith("http://127.0.0.1/images/"));assertTrue(Files.exists(images.resolve(url.substring(url.lastIndexOf('/')+1))));
        mvc.perform(put("/api/user/edit-profile").with(mvc.as(shopper)).contentType("application/json").content(json.writeValueAsString(Map.of("profileImageUrl",url)))).andExpect(status().isOk()).andExpect(jsonPath("$.profileImageUrl").value(url));
        mvc.perform(get("/api/auth/session").with(mvc.as(shopper))).andExpect(jsonPath("$.user.profileImageUrl").value(url));
        mvc.perform(multipart("/api/user/profile-image").file(new MockMultipartFile("file","renamed.png","image/png","not an image".getBytes())).with(mvc.as(shopper))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/user/edit-profile").with(mvc.as(shopper)).contentType("application/json").content("{\"profileImageUrl\":\"\"}")).andExpect(status().isOk());assertEquals("",users.findByUsername("photo-user").orElseThrow().getProfileImageUrl());
    }
}
