package com.bingo.app.bot.command;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.i18n.BotText;
import com.bingo.app.bot.onboarding.PasswordOnboardingService;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * The only command the bot has: {@code /start <inviteCode>} registers the
 * person, and everything after that is phone plus password. No game menu.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartCommand {

    private final UserService userService;
    private final InviteService inviteService;
    private final PasswordOnboardingService passwordOnboarding;
    private final BotText text;

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    public void handle(Update update, BingoTelegramBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        String message = update.getMessage().getText();
        String code = extractStartCode(message);
        Long chatId = update.getMessage().getChatId();

        log.info("Start command from telegramId={}, code={}", telegramId, code);

        // A fresh /start abandons any half-typed password from before.
        passwordOnboarding.reset(bot, chatId);

        User existingUser = userService.findByTelegramId(telegramId);
        if (existingUser != null) {
            // PLAYER with an invite code → try to upgrade to ADMIN
            if (code != null && !code.isBlank() && existingUser.getRole() == Role.PLAYER) {
                try {
                    User upgraded = inviteService.registerWithInvite(telegramId, code);
                    TenantHelper.runWithTenant(upgraded, () -> {
                        sendMessage(bot, chatId, text.t("registered_admin"));
                        continueRegistration(bot, chatId, upgraded);
                    });
                    return;
                } catch (Exception e) {
                    log.warn("Invite upgrade failed for telegramId={}, falling back", telegramId, e);
                }
            }

            sendMessage(bot, chatId, text.t("welcome_back"));
            continueRegistration(bot, chatId, existingUser);
            return;
        }

        // Super admin registration
        if (telegramId.equals(superAdminTelegramId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            sendMessage(bot, chatId, text.t("super_admin_welcome"));
            continueRegistration(bot, chatId, superAdmin);
            return;
        }

        // New user with invite code
        if (code == null || code.isBlank()) {
            sendMessage(bot, chatId, text.t("invalid_invite"));
            return;
        }

        try {
            User newUser = inviteService.registerWithInvite(telegramId, code);
            sendMessage(bot, chatId, newUser.getRole() == Role.ADMIN
                    ? text.t("registered_admin")
                    : text.t("registered_player"));
            continueRegistration(bot, chatId, newUser);
        } catch (Exception e) {
            log.error("Registration failed for telegramId={}", telegramId, e);
            sendMessage(bot, chatId, text.t("registration_failed", "reason", safeReason(e)));
        }
    }

    /**
     * Registration is only finished once the phone number is known and a
     * password exists, so the menu is replaced by those two steps.
     */
    private void continueRegistration(BingoTelegramBot bot, Long chatId, User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            passwordOnboarding.requestPhoneNumber(bot, chatId, text.t("phone_request_full"));
            return;
        }
        if (user.getTelegramUsername() == null || user.getTelegramUsername().isBlank()) {
            sendMessage(bot, chatId, text.t("username_tip"));
        }
        passwordOnboarding.prompt(bot, chatId, user);
    }

    private String extractStartCode(String message) {
        String[] parts = message.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    /** The raw exception text is English-only and can be long; keep it short. */
    private String safeReason(Exception e) {
        String reason = e.getMessage();
        return reason == null || reason.isBlank() ? text.one("error_generic", "en") : reason;
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String body) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(body)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }
}
