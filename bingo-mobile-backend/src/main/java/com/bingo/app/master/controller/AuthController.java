package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.TelegramAuthService;
import com.bingo.app.infrastructure.security.TelegramAuthException;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.dto.request.ConfirmPasswordResetRequest;
import com.bingo.app.master.dto.request.LoginRequest;
import com.bingo.app.master.dto.request.PasswordResetRequest;
import com.bingo.app.master.dto.request.PasswordStatusRequest;
import com.bingo.app.master.dto.request.PhoneLoginRequest;
import com.bingo.app.master.dto.request.RegisterPushTokenRequest;
import com.bingo.app.master.dto.response.AuthResponse;
import com.bingo.app.master.dto.response.PasswordStatusResponse;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.master.service.PasswordResetService;
import com.bingo.app.master.service.PhonePasswordAuthService;
import com.bingo.app.master.service.PushTokenService;
import com.bingo.app.master.service.UserProfileService;

import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PasswordResetService passwordResetService;
    private final PushTokenService pushTokenService;
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

    /**
     * Stores the device's Expo push token against the logged-in user so the
     * backend can raise a system notification at game-start countdown even when
     * this user is out of the app.
     */
    @PostMapping("/push-token")
    public ApiResponse<Void> registerPushToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterPushTokenRequest request) {
        pushTokenService.register(principal.getUser().getId(), request.token());
        return ApiResponse.ok(null);
    }

    /** Lets the mobile app show the "open the bot to create a password" screen. */
    @PostMapping("/password/status")
    public ApiResponse<PasswordStatusResponse> passwordStatus(@Valid @RequestBody PasswordStatusRequest request) {
        boolean hasPassword = phonePasswordAuthService.hasPassword(request.phone());
        return ApiResponse.ok(new PasswordStatusResponse(hasPassword));
    }

    /**
     * Starts a password reset: issues a one-time 6-digit code and delivers it via
     * Telegram to the phone's owner. The response is identical whether or not the
     * phone has an account (so numbers can't be harvested); an account with no
     * password yet reports 421 {@code no_password} so the app routes to the bot.
     */
    @PostMapping("/password/reset/request")
    public ApiResponse<String> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.phone());
        return ApiResponse.ok("If an account exists for that phone, a reset code was sent.");
    }

    /**
     * Completes a password reset with the one-time code. Success returns a fresh
     * JWT + profile (identical to phone login) so the app can sign the user in.
     */
    @PostMapping("/password/reset/confirm")
    public ApiResponse<AuthResponse> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        AuthResponse auth = passwordResetService.confirmReset(
                request.phone(), request.code(), request.newPassword());
        return ApiResponse.ok("Password reset", auth);
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