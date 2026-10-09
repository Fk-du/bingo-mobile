package com.bingo.app.master.service;

import com.bingo.app.infrastructure.security.JwtTokenService;
import com.bingo.app.infrastructure.security.PhoneAuthException;
import com.bingo.app.master.dto.response.AuthResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.PasswordResetCode;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.exception.PasswordResetException;
import com.bingo.app.master.repository.PasswordResetCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * The one-time reset code: issued to Telegram, single-use, expired after ten
 * minutes, burned after five wrong attempts, and only its hash is stored.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock PasswordResetCodeRepository resetRepository;
    @Mock UserService userService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserProfileService userProfileService;
    @Mock JwtTokenService jwtTokenService;
    @Mock NotificationService notificationService;

    @InjectMocks PasswordResetService service;

    private User activeUserWithPassword() {
        return User.builder()
                .id(7L)
                .telegramId(111L)
                .phoneNumber("251911223344")
                .passwordHash("$2a$10$alreadyHashed")
                .role(Role.PLAYER)
                .active(true)
                .build();
    }

    private PasswordResetCode openCode(String rawCode, int attempts, boolean expired) {
        return PasswordResetCode.builder()
                .id(1L)
                .userId(7L)
                .phoneNumber("251911223344")
                .codeHash(sha256Hex(rawCode))
                .expiresAt(expired ? LocalDateTime.now().minusMinutes(1) : LocalDateTime.now().plusMinutes(9))
                .attempts(attempts)
                .used(false)
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();
    }

    @Test
    @DisplayName("unknown phones get the same silence as any other: no code, no Telegram message")
    void unknownPhoneIsSilent() {
        when(userService.findByPhoneNumber("+251911223344")).thenReturn(null);

        service.requestReset("+251911223344");

        verify(resetRepository, never()).save(any());
        verify(notificationService, never()).sendResetCode(anyLong(), anyString());
    }

    @Test
    @DisplayName("an account with no password is routed to the bot like login (421)")
    void noPasswordRoutesToBot() {
        User silent = User.builder().id(7L).telegramId(111L).active(true).build();
        when(userService.findByPhoneNumber("251911223344")).thenReturn(silent);

        assertThrows(PhoneAuthException.class, () -> service.requestReset("251911223344"));
    }

    @Test
    @DisplayName("a valid request stores only a hash and delivers the 6-digit code via Telegram")
    void issuesCodeAndSendsTelegram() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());
        when(resetRepository.findAllByUserId(7L)).thenReturn(List.of());
        when(resetRepository.save(any(PasswordResetCode.class))).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("251911223344");

        var captor = org.mockito.ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(resetRepository).save(captor.capture());
        PasswordResetCode saved = captor.getValue();
        assertEquals(7L, saved.getUserId());

        var telegram = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendResetCode(eq(7L), telegram.capture());
        assertTrue(saved.getCodeHash().matches("[0-9a-f]{64}"), "hash must not be the plain code");
        assertTrue(telegram.getValue().matches("(?s).*\\b\\d{6}\\b.*"), "message carries the 6-digit code");
        assertNotEquals(telegram.getValue().substring(telegram.getValue().indexOf("code is ") + 9, telegram.getValue().indexOf("code is ") + 15),
                saved.getCodeHash(), "plain code must not be stored");
    }

    @Test
    @DisplayName("a re-request within the resend delay keeps the standing code")
    void recentCodeSuppressesResend() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());
        when(resetRepository.findAllByUserId(7L))
                .thenReturn(List.of(openCode("123456", 0, false)));

        service.requestReset("251911223344");

        verify(resetRepository, never()).save(any(PasswordResetCode.class));
        verify(notificationService, never()).sendResetCode(anyLong(), anyString());
    }

    @Test
    @DisplayName("the correct code resets the password and returns a JWT (auto-login)")
    void correctCodeResetsAndReturnsAuth() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());
        when(resetRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(openCode("123456", 0, false)));
        when(resetRepository.markUsed(1L)).thenReturn(1);
        when(passwordEncoder.encode("new-secret")).thenReturn("$2a$10$fresh");
        when(userProfileService.buildProfile(any(User.class))).thenReturn(UserProfileResponse.builder()
                .id(7L).role("PLAYER").build());
        when(jwtTokenService.issue(any(User.class))).thenReturn("jwt-token");

        AuthResponse auth = service.confirmReset("251911223344", "123456", "new-secret");

        verify(userService).setPassword(7L, "$2a$10$fresh");
        verify(resetRepository).markUsed(1L);
        assertEquals("jwt-token", auth.jwt());
    }

    @Test
    @DisplayName("a wrong code bumps the attempt counter and only a matching hash passes")
    void wrongCodeIncrementsAttempts() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());
        when(resetRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(openCode("123456", 1, false)));

        assertThrows(PasswordResetException.class, () -> service.confirmReset("251911223344", "999999", "new-secret"));

        var captor = org.mockito.ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(resetRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getAttempts());
        verify(resetRepository, never()).markUsed(anyLong());
    }

    @Test
    @DisplayName("five wrong attempts invalidate the code for good")
    void fiveWrongAttemptsInvalidate() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());
        when(resetRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(openCode("123456", 4, false)));

        PasswordResetException ex = assertThrows(PasswordResetException.class,
                () -> service.confirmReset("251911223344", "999999", "new-secret"));

        assertEquals("otp_max_attempts", ex.getCode());
        verify(resetRepository).invalidateAll(7L);
    }

    @Test
    @DisplayName("an expired code is invalidated instead of used")
    void expiredCodeIsRejected() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());
        when(resetRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(openCode("123456", 0, true)));

        PasswordResetException ex = assertThrows(PasswordResetException.class,
                () -> service.confirmReset("251911223344", "123456", "new-secret"));

        assertEquals("otp_expired", ex.getCode());
        verify(resetRepository).invalidateAll(7L);
        verify(userService, never()).setPassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("a short new password is refused outright")
    void weakPasswordIsRefused() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());

        PasswordResetException ex = assertThrows(PasswordResetException.class,
                () -> service.confirmReset("251911223344", "123456", "abc"));

        assertEquals("weak_password", ex.getCode());
    }

    @Test
    @DisplayName("a code that was already consumed cannot be replayed")
    void consumedCodeCannotBeReplayed() {
        when(userService.findByPhoneNumber("251911223344")).thenReturn(activeUserWithPassword());
        when(resetRepository.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(openCode("123456", 0, false)));
        when(resetRepository.markUsed(1L)).thenReturn(0);

        assertThrows(PasswordResetException.class, () -> service.confirmReset("251911223344", "123456", "new-secret"));
        verify(userService, never()).setPassword(anyLong(), anyString());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}