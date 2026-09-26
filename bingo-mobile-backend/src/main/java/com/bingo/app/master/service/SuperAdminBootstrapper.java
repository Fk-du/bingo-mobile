package com.bingo.app.master.service;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps a SUPER_ADMIN for phone+password (mobile) deployments. When
 * {@code SUPER_ADMIN_PHONE} and {@code SUPER_ADMIN_PASSWORD} are configured, it
 * ensures a super-admin exists whose phone can be used with the mobile app
 * (JWT login) — complementing the Telegram-based super admin from
 * {@code SUPER_ADMIN_TELEGRAM_ID}. Idempotent.
 */
@Component
@ConditionalOnProperty(prefix = "app.super-admin", name = {"phone", "password"})
@Slf4j
public class SuperAdminBootstrapper {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.phone:}")
    private String superAdminPhone;

    @Value("${app.super-admin.password:}")
    private String superAdminPassword;

    public SuperAdminBootstrapper(UserRepository userRepository,
                                  UserService userService,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSuperAdmin() {
        try {
            if (superAdminPhone == null || superAdminPhone.isBlank()) {
                return;
            }

            // Store the phone in the same normalized form the login path uses.
            String normalized = UserService.normalizePhone(superAdminPhone.trim());
            if (normalized == null) {
                return;
            }

            User admin = userRepository.findByPhoneNumber(normalized).orElse(null);

            if (admin == null) {
                // No user holds this phone. Prefer the configured Telegram super
                // admin identity so the same row works for web (telegram) and
                // mobile (phone+password); otherwise create a placeholder row.
                admin = userService.createSuperAdminForMobile(normalized);
            }

            admin.setRole(Role.SUPER_ADMIN);
            admin.setAdminApproved(true);
            admin.setActive(true);
            admin.setPhoneNumber(normalized);
            if (superAdminPassword != null && !superAdminPassword.isBlank()) {
                admin.setPasswordHash(passwordEncoder.encode(superAdminPassword));
            }
            userRepository.save(admin);
            log.info("Super admin (phone+password) ensured: phone={}", superAdminPhone);
        } catch (Exception e) {
            log.warn("Super admin bootstrap failed: {}", e.getMessage());
        }
    }
}