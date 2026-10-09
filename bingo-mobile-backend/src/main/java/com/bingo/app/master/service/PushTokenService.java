package com.bingo.app.master.service;

import com.bingo.app.common.exception.BadRequestException;
import com.bingo.app.common.exception.NotFoundException;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the device's Expo push token against the logged-in user's ticket. A
 * token is bound to one device, so this latest one wins — a player switching
 * phones replaces the old token, and a token Expo no longer recognizes is
 * cleared by {@link NotificationService} when a push bounces.
 */
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private static final String TOKEN_PREFIX = "ExponentPushToken[";
    private static final int MAX_TOKEN_LENGTH = 512;

    private final UserRepository userRepository;

    @Transactional(transactionManager = "masterTransactionManager")
    public void register(Long userId, String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH
                || !token.startsWith(TOKEN_PREFIX) || !token.endsWith("]")) {
            throw new BadRequestException("Invalid push token");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setPushToken(token);
        userRepository.save(user);
    }
}