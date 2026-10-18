package com.mercato.Configuration;

import com.mercato.Entity.AppRole;
import com.mercato.Entity.EcommUser;
import com.mercato.Entity.Role;
import com.mercato.Repository.RoleRepository;
import com.mercato.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@org.springframework.core.annotation.Order(0)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.phone}")
    private String adminPhone;

    @Override
    public void run(String... args) {
        createRoleIfNotExists(AppRole.ROLE_USER);
        createRoleIfNotExists(AppRole.ROLE_SELLER);
        createRoleIfNotExists(AppRole.ROLE_ADMIN);
        createAdminIfNotExists();
    }

    private void createRoleIfNotExists(AppRole roleName) {
        roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    private void createAdminIfNotExists() {
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Admin user already exists, skipping creation.");
            return;
        }

        Role adminRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("ROLE_ADMIN not found"));
        Role sellerRole = roleRepository.findByRoleName(AppRole.ROLE_SELLER)
                .orElseThrow(() -> new RuntimeException("ROLE_SELLER not found"));

        EcommUser admin = EcommUser.builder()
                .username(adminUsername)
                .email(adminEmail)
                .password(encoder.encode(adminPassword))
                .firstName("Admin")
                .phoneNumber(adminPhone)
                .enabled(true)
                .accountLocked(false)
                .emailVerified(true)
                .roles(Set.of(adminRole, sellerRole))
                .sellerDisplayName("Marcato Admin")
                .build();

        userRepository.save(admin);
        log.info("Admin user '{}' created successfully.", adminUsername);
    }
}
