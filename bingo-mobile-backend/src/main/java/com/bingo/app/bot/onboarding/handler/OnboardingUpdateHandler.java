package com.bingo.app.bot.onboarding.handler;

import com.bingo.app.bot.i18n.BotText;
import com.bingo.app.bot.onboarding.OnboardingBot;
import com.bingo.app.bot.onboarding.PasswordOnboardingService;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

/**
 * Minimal flow for the registration-only bot:
 * <ol>
 *   <li>{@code /start <inviteCode>} — register via {@link InviteService} (same role/tenant logic as the game bot).</li>
 *   <li>Share phone via {@code request_contact} — {@link UserService#savePhoneNumber}.</li>
 *   <li>Optional "Create Password" — handled by {@link PasswordOnboardingService}.</li>
 * </ol>
 * No game menu; the game bot and web app are untouched.
 *
 * <p>The password step itself lives in {@link PasswordOnboardingService} so the
 * game bot and this bot end registration in exactly the same state.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OnboardingUpdateHandler {

    private final InviteService inviteService;
    private final UserService userService;
    private final PasswordOnboardingService passwordOnboarding;
    private final BotText botText;

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    public void handle(Update update, OnboardingBot bot) {
        if (update == null) {
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            if (messageText != null && messageText.startsWith("/start")) {
                handleStart(update, bot);
                return;
            }
            handleText(update, bot);
            return;
        }
        if (update.hasMessage() && update.getMessage().hasContact()) {
            handleContact(update, bot);
            return;
        }
        if (update.hasCallbackQuery()) {
            passwordOnboarding.handleCallback(bot, update);
        }
    }

    private void handleStart(Update update, OnboardingBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String code = extractStartCode(update.getMessage().getText());
        passwordOnboarding.reset(bot, chatId);

        User existing = userService.findByTelegramId(telegramId);
        if (existing != null) {
            sendText(bot, chatId, botText.t("welcome_back"));
            continueOnboarding(bot, chatId, existing);
            return;
        }

        if (telegramId.equals(superAdminTelegramId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            sendText(bot, chatId, botText.t("super_admin_welcome"));
            continueOnboarding(bot, chatId, superAdmin);
            return;
        }

        if (code == null || code.isBlank()) {
            sendText(bot, chatId, botText.t("invalid_invite"));
            return;
        }

        try {
            User newUser = inviteService.registerWithInvite(telegramId, code);
            TenantHelper.runWithTenant(newUser, () -> {
                sendText(bot, chatId, newUser.getRole() == com.bingo.app.master.enums.Role.ADMIN
                        ? botText.t("registered_admin")
                        : botText.t("registered_player"));
                continueOnboarding(bot, chatId, newUser);
            });
        } catch (Exception e) {
            log.error("Registration failed for telegramId={}", telegramId, e);
            sendText(bot, chatId, botText.t("registration_failed", "reason", e.getMessage() == null
                    ? botText.one("error_generic", "en") : e.getMessage()));
        }
    }

    private void handleContact(Update update, OnboardingBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        var contact = update.getMessage().getContact();

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            sendText(bot, chatId, botText.t("unknown_user_need_invite_short"));
            return;
        }

        if (contact.getPhoneNumber() != null
                && (contact.getUserId() == null || contact.getUserId().equals(telegramId))) {
            userService.savePhoneNumber(telegramId, contact.getPhoneNumber());
        }

        User fresh = userService.findByTelegramId(telegramId);
        if (fresh.getPhoneNumber() == null || fresh.getPhoneNumber().isBlank()) {
            sendText(bot, chatId, botText.t("phone_read_failed"));
            requestPhoneNumber(bot, chatId);
            return;
        }

        sendText(bot, chatId, botText.t("phone_saved"));
        passwordOnboarding.prompt(bot, chatId, fresh);
    }

    private void handleText(Update update, OnboardingBot bot) {
        Long chatId = update.getMessage().getChatId();
        if (passwordOnboarding.isCapturing(bot, chatId)) {
            passwordOnboarding.handleText(bot, update);
            return;
        }
        sendText(bot, chatId, botText.t("nothing_to_type"));
    }

    private void continueOnboarding(OnboardingBot bot, Long chatId, User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            requestPhoneNumber(bot, chatId);
            return;
        }
        sendText(bot, chatId, botText.t("registered_with_phone", "phone", user.getPhoneNumber()));
        passwordOnboarding.prompt(bot, chatId, user);
    }

    public void requestPhoneNumber(OnboardingBot bot, Long chatId) {
        passwordOnboarding.requestPhoneNumber(bot, chatId, botText.t("phone_request_short"));
    }

    private String extractStartCode(String text) {
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    private void sendText(OnboardingBot bot, Long chatId, String text) {
        try {
            bot.execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}
