package com.bingo.app.master.service;

import com.bingo.app.common.exception.BadRequestException;
import com.bingo.app.common.exception.NotFoundException;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The device push token is bound to the logged-in user, latest one wins, and
 * malformed tokens are refused (nothing the app sends can poison the column).
 */
@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks PushTokenService service;

    private User userWithId(Long id) {
        return User.builder().id(id).telegramId(100L + id).role(com.bingo.app.master.enums.Role.PLAYER).active(true).build();
    }

    @Test
    @DisplayName("a valid Expo token is stored on the user")
    void storesValidToken() {
        User user = userWithId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        String token = "ExponentPushToken[abc123]";
        service.register(1L, token);

        assertEquals(token, user.getPushToken());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("a second device replaces the stored token (latest phone wins)")
    void replacesOnNewDevice() {
        User user = userWithId(2L);
        user.setPushToken("ExponentPushToken[oldDevice]");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.register(2L, "ExponentPushToken[newDevice]");

        assertEquals("ExponentPushToken[newDevice]", user.getPushToken());
    }

    @Test
    @DisplayName("malformed tokens are refused before any lookup")
    void refusesMalformedTokens() {
        assertThrows(BadRequestException.class, () -> service.register(3L, "not-a-token"));
        assertThrows(BadRequestException.class, () -> service.register(3L, ""));
        assertThrows(BadRequestException.class, () -> service.register(3L, "ExponentPushToken[no closing bracket"));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("an unknown user cannot attach a token")
    void unknownUserRejected() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.register(9L, "ExponentPushToken[abc]"));
    }
}