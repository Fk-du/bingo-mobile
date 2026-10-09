package com.bingo.app.master.service;

import com.bingo.app.infrastructure.security.JwtTokenService;
import com.bingo.app.infrastructure.security.PhoneAuthException;
import com.bingo.app.master.dto.response.AuthResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.PasswordResetCode;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.exception.PasswordResetException;
import com.bingo.app.master.repository.PasswordResetCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Phone-verified password reset for the mobile app. The phone number is the
 * identity, and the only channel that provably reaches its owner is Telegram
 * (the number was originally shared through a Telegram contact button), so the
 * one-time reset code is delivered there.
 *
 * <p>A code is six digits, lives 10 minutes, and is single-use: it is burned on
 * a successful reset, on expiry, and after five wrong entries. Only its SHA-256
 * hash is stored. The request endpoint answers identically whether or not the
 * phone exists, so callers cannot harvest registered numbers.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    static final int CODE_LIFETIME_MINUTES = 10;
    static final long RESEND_DELAY_SECONDS = 60;
    static final int CODE_LENGTH = 6;
    static final int MIN_PASSWORD_LENGTH = 4;

    private final PasswordResetCodeRepository resetRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;
    private final JwtTokenService jwtTokenService;
    private final NotificationService notificationService;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Issues a one-time reset code for the phone's owner and sends it to their
     * Telegram. Deliberately silent for unknown or suspended numbers; an account
     * that never set a password instead reports {@code no_password} (421) so the
     * app can route the user to the registration bot, mirroring login.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public void requestReset(String phone) {
        User user = userService.findByPhoneNumber(phone);
        if (user == null || !user.isActive()) {
            log.info("Password reset requested for {}: no active account", mask(phone));
            return;
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            log.info("Password reset requested for {}: account has no password", mask(phone));
            throw PhoneAuthException.noPassword();
        }

        if (recentUnusedCodeExists(user.getId())) {
            log.info("Password reset re-request for user id={}: within resend delay, keeping code", user.getId());
            return;
        }

        resetRepository.invalidateAll(user.getId());

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        PasswordResetCode resetCode = PasswordResetCode.builder()
                .userId(user.getId())
                .phoneNumber(user.getPhoneNumber() == null ? "" : user.getPhoneNumber())
                .codeHash(sha256Hex(code))
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_LIFETIME_MINUTES))
                .build();
        resetRepository.save(resetCode);

        notificationService.sendResetCode(user.getId(),
                "Your BingoPlus password reset code is " + code
                        + ". It expires in " + CODE_LIFETIME_MINUTES + " minutes and can be used once.");
        log.info("Password reset code issued for user id={}", user.getId());
    }

    /**
     * Verifies the code and sets the new password. The code is consumed exactly
     * once: a concurrent double-submit loses, because the "mark used" update is
     * atomic and conditional. Success returns the same JWT + profile as login,
     * so the app can sign the user straight in.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public AuthResponse confirmReset(String phone, String code, String newPassword) {
        User user = userService.findByPhoneNumber(phone);
        if (user == null || !user.isActive()) {
            throw PasswordResetException.invalidCode();
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw PasswordResetException.weakPassword(MIN_PASSWORD_LENGTH);
        }

        PasswordResetCode reset = resetRepository
                .findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(PasswordResetException::invalidCode);

        if (reset.getExpiresAt() != null && reset.getExpiresAt().isBefore(LocalDateTime.now())) {
            resetRepository.invalidateAll(user.getId());
            throw PasswordResetException.expired();
        }
        if (reset.getAttempts() >= PasswordResetException.maxAttempts()) {
            resetRepository.invalidateAll(user.getId());
            throw PasswordResetException.tooManyAttempts();
        }

        if (!constantTimeEquals(sha256Hex(code == null ? "" : code.trim()), reset.getCodeHash())) {
            reset.setAttempts(reset.getAttempts() + 1);
            resetRepository.save(reset);
            log.warn("Wrong reset code for user id={} (attempt {})", user.getId(), reset.getAttempts());
            if (reset.getAttempts() >= PasswordResetException.maxAttempts()) {
                resetRepository.invalidateAll(user.getId());
                throw PasswordResetException.tooManyAttempts();
            }
            throw PasswordResetException.invalidCode();
        }

        int consumed = resetRepository.markUsed(reset.getId());
        if (consumed == 0) {
            throw PasswordResetException.invalidCode();
        }

        userService.setPassword(user.getId(), passwordEncoder.encode(newPassword));
        UserProfileResponse profile = userProfileService.buildProfile(user);
        String jwt = jwtTokenService.issue(user);
        log.info("Password reset by user id={}, role={}", user.getId(), user.getRole());
        return new AuthResponse(jwt, profile);
    }

    private boolean recentUnusedCodeExists(Long userId) {
        List<PasswordResetCode> open = resetRepository.findAllByUserId(userId).stream()
                .filter(c -> !c.isUsed())
                .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(LocalDateTime.now()))
                .toList();
        return open.stream().anyMatch(c ->
                c.getCreatedAt() != null
                        && c.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESEND_DELAY_SECONDS)));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private static String mask(String phone) {
        String normalized = UserService.normalizePhone(phone);
        if (normalized == null || normalized.length() < 4) {
            return "****";
        }
        return normalized.substring(0, normalized.length() - 4) + "****";
    }
}