package com.bingo.app.master.service;

import com.bingo.app.infrastructure.security.JwtTokenService;
import com.bingo.app.infrastructure.security.PhoneAuthException;
import com.bingo.app.master.dto.response.AuthResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Authenticates mobile-app users with phone + password. Mirrors the status
 * boundary of the Telegram path: inactive/suspended users are rejected and
 * unapproved admins are returned in the pending state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhonePasswordAuthService {

    private final UserService userService;
    private final UserProfileService userProfileService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(String phone, String rawPassword) {
        if (phone == null || phone.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw PhoneAuthException.invalidCredentials();
        }

        User user = userService.findByPhoneNumber(phone.trim());
        if (user == null) {
            log.info("Phone login failed: no account for phone");
            throw PhoneAuthException.invalidCredentials();
        }

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            log.info("Phone login failed (NO_PASSWORD): phone has no password set");
            throw PhoneAuthException.noPassword();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.info("Phone login failed: wrong password for user id={}", user.getId());
            throw PhoneAuthException.invalidCredentials();
        }

        if (!user.isActive()) {
            log.warn("Phone login blocked: suspended user id={}", user.getId());
            throw PhoneAuthException.suspended();
        }

        UserProfileResponse profile = userProfileService.buildProfile(user);
        String jwt = jwtTokenService.issue(user);
        log.info("Phone login success: user id={}, role={}", user.getId(), user.getRole());
        return new AuthResponse(jwt, profile);
    }

    /**
     * Whether the account for the given phone has a password set. Unknown phones
     * return {@code false} so the app routes them to the registration bot.
     */
    public boolean hasPassword(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        User user = userService.findByPhoneNumber(phone.trim());
        if (user == null) {
            return false;
        }
        return user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
    }
}