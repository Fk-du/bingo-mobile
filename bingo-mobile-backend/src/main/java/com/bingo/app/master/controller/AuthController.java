package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.TelegramAuthService;
import com.bingo.app.infrastructure.security.TelegramAuthException;
import com.bingo.app.master.dto.request.LoginRequest;
import com.bingo.app.master.dto.request.PasswordStatusRequest;
import com.bingo.app.master.dto.request.PhoneLoginRequest;
import com.bingo.app.master.dto.response.AuthResponse;
import com.bingo.app.master.dto.response.PasswordStatusResponse;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.master.service.PhonePasswordAuthService;
import com.bingo.app.master.service.UserProfileService;

import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TelegramAuthService telegramAuthService;
    private final UserProfileService userProfileService;
    private final PhonePasswordAuthService phonePasswordAuthService;
    private final ConfigService configService;

    @PostMapping("/login")
    public ApiResponse<UserProfileResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = telegramAuthService.authenticate(request.initData(), request.startParam());
        if (user == null) {
            throw new TelegramAuthException("Authentication failed",
                    "Telegram authentication failed. Open the app from Telegram and try again.",
                    "authentication_failed");
        }
        return ApiResponse.ok("Authenticated", userProfileService.buildProfile(user));
    }

    /**
     * Mobile-app (phone + password) login. Returns the JWT and full profile on
     * success; 421 with code {@code no_password} when the account has no password.
     */
    @PostMapping("/phone/login")
    public ApiResponse<AuthResponse> phoneLogin(@Valid @RequestBody PhoneLoginRequest request) {
        AuthResponse auth = phonePasswordAuthService.login(request.phone(), request.password());
        return ApiResponse.ok("Authenticated", auth);
    }

    /** Lets the mobile app show the "open the bot to create a password" screen. */
    @PostMapping("/password/status")
    public ApiResponse<PasswordStatusResponse> passwordStatus(@Valid @RequestBody PasswordStatusRequest request) {
        boolean hasPassword = phonePasswordAuthService.hasPassword(request.phone());
        return ApiResponse.ok(new PasswordStatusResponse(hasPassword));
    }

    /**
     * Public, unauthenticated config shared with the mobile app (the NO_PASSWORD
     * screen needs the registration bot username before the user has logged in).
     */
    @GetMapping("/public-config")
    public ApiResponse<Map<String, Object>> publicConfig() {
        return ApiResponse.ok(configService.publicInfo());
    }
}