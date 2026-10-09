package com.bingo.app.master.service;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.infrastructure.push.ExpoPushClient;
import com.bingo.app.master.entity.Notification;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.NotificationRepository;
import com.bingo.app.master.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ExpoPushClient expoPushClient;
    private final ObjectProvider<BingoTelegramBot> botProvider;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final ObjectMapper objectMapper;

    private final ExecutorService telegramExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService pushExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${bingo.webapp.url}")
    private String webAppUrl;

    @PreDestroy
    public void shutdown() {
        telegramExecutor.shutdownNow();
        pushExecutor.shutdownNow();
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body) {
        return notify(userId, type, title, body, null, null, null, null, null);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body,
                               String referenceType, Long referenceId) {
        return notify(userId, type, title, body, null, null, referenceType, referenceId, null);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body,
                               String referenceType, Long referenceId, String telegramText) {
        return notify(userId, type, title, body, null, null, referenceType, referenceId, telegramText);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body,
                               String messageKey, String messageParams) {
        return notify(userId, type, title, body, messageKey, messageParams, null, null, null);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body,
                               String messageKey, String messageParams,
                               String referenceType, Long referenceId) {
        return notify(userId, type, title, body, messageKey, messageParams, referenceType, referenceId, null);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body,
                               String messageKey, String messageParams,
                               String referenceType, Long referenceId, String telegramText) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .messageKey(messageKey)
                .messageParams(messageParams)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();

        Notification saved = notificationRepository.saveAndFlush(notification);
        pushWebSocket(saved);

        if (telegramText != null && !telegramText.isBlank()) {
            sendTelegram(userId, telegramText);
        }

        return saved;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<Notification> getForUser(Long userId, int limit) {
        return limit <= 50
                ? notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                : notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .ifPresent(n -> {
                    if (n.getReadAt() == null) {
                        n.setReadAt(java.time.LocalDateTime.now());
                        notificationRepository.save(n);
                    }
                });
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public long markAllRead(Long userId) {
        int updated = notificationRepository.markAllRead(userId);
        return updated;
    }

    private void pushWebSocket(Notification notification) {
        try {
            SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
            if (messagingTemplate == null) return;

            User user = userRepository.findById(notification.getUserId()).orElse(null);
            if (user == null) return;
            String principalName = user.getTelegramId() != null
                    ? String.valueOf(user.getTelegramId()) : String.valueOf(user.getId());

            String payload = objectMapper.writeValueAsString(
                    Map.of("type", "NOTIFICATION", "data", notification));
            messagingTemplate.convertAndSendToUser(principalName, "/queue/notifications", payload);
        } catch (Exception e) {
            log.warn("Failed to push notification via WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Sends a one-time password-reset code straight to the user's Telegram chat.
     * Deliberately does <em>not</em> persist a {@link Notification} row: the code
     * must not live in the database in clear text (its hash is kept in
     * {@code password_reset_codes}). Accounts without a real Telegram chat (the
     * synthetic mobile-only id) fail silently here, and the code simply can't be
     * delivered.
     */
    public void sendResetCode(Long userId, String text) {
        sendTelegram(userId, text);
    }

    /**
     * Device push to every player registered in a game, fired the moment a
     * brand-new game enters its starting countdown, so players who have left the
     * app still get a system notification. Non-blocking: the batch is sent on a
     * virtual thread and tokens Expo no longer recognizes are dropped.
     */
    public void sendGameStartingPush(Long gameId, List<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return;
        }
        pushExecutor.execute(() -> {
            try {
                Map<String, User> byToken = new HashMap<>();
                for (User user : userRepository.findAllById(playerIds)) {
                    String token = user.getPushToken();
                    if (token != null && !token.isBlank()) {
                        byToken.put(token, user);
                    }
                }
                if (byToken.isEmpty()) {
                    return;
                }

                List<ExpoPushClient.PushMessage> messages = byToken.keySet().stream()
                        .map(token -> new ExpoPushClient.PushMessage(
                                token,
                                "Game starting!",
                                "A game you joined is starting now — open the app to play.",
                                Map.of("gameId", gameId)))
                        .toList();

                List<String> invalid = expoPushClient.send(messages);
                if (!invalid.isEmpty()) {
                    for (String token : invalid) {
                        User user = byToken.get(token);
                        if (user != null) {
                            user.setPushToken(null);
                            userRepository.save(user);
                        }
                    }
                    log.info("Cleared {} expired push token(s) after game-start push for game {}", invalid.size(), gameId);
                }
            } catch (Exception e) {
                log.warn("Failed to send game-start push for game {}: {}", gameId, e.getMessage());
            }
        });
    }

    private void sendTelegram(Long userId, String text) {
        telegramExecutor.execute(() -> {
            try {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || user.getTelegramId() == null) return;
                BingoTelegramBot bot = botProvider.getIfAvailable();
                if (bot == null) return;

                SendMessage message = SendMessage.builder()
                        .chatId(user.getTelegramId())
                        .text(text)
                        .build();

                bot.execute(message);
            } catch (Exception e) {
                log.debug("Telegram push failed for user {}: {}", userId, e.getMessage());
            }
        });
    }
}