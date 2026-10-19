package com.mercato.Configuration;

import com.mercato.Entity.*;
import com.mercato.Payloads.Request.SellerApplicationRequest;
import com.mercato.Repository.*;
import com.mercato.Service.SellerOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

@Component @Profile("demo & !prod") @Order(2) @RequiredArgsConstructor
public class DemoAccountsInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final SellerOnboardingService onboarding;

    private EcommUser account(String username, String password, String phone, String firstName) {
        return users.save(EcommUser.builder().username(username).email(username + "@mercato.invalid")
                .password(passwords.encode(password)).phoneNumber(phone).firstName(firstName).enabled(true).emailVerified(true)
                .roles(new HashSet<>(Set.of(roles.findByRoleName(AppRole.ROLE_USER).orElseThrow()))).build());
    }

    @Override @Transactional public void run(String... args) {
        if (!users.existsByUsername("shopperreview")) account("shopperreview", "ShopOnly!2026", "9188888801", "Alex");
        if (users.existsByUsername("sellerreview")) return;
        EcommUser applicant = account("sellerreview", "SellerOnly!2026", "9188888802", "Taylor");
        SellerApplicationRequest form = new SellerApplicationRequest();
        form.setStoreName("Sample Store — Demo"); form.setLegalBusinessName("Synthetic Review Business");
        form.setBusinessType("SOLE_PROPRIETOR"); form.setPan("AAAAA9999A"); form.setGstin("29AAAAA9999A1Z5");
        form.setTaxRegistrationType("GST_REGISTERED"); form.setPickupAddress("12 Sample Pickup Lane, Bengaluru");
        form.setPickupState("Karnataka"); form.setPickupPincode("560001"); form.setSupportEmail("sellerreview@mercato.invalid");
        form.setProductCategories("Home & Living — synthetic review data"); form.setPolicyVersion("2026-10-draft"); form.setPoliciesAcknowledged(true);
        onboarding.submit(applicant, form);
    }
}
