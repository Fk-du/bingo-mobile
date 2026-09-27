package com.bingo.app.bot.onboarding.handler;

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

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    public void handle(Update update, OnboardingBot bot) {
        if (update == null) {
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            if (text != null && text.startsWith("/start")) {
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
            sendText(bot, chatId, "👋 Welcome back! You are already registered.");
            continueOnboarding(bot, chatId, existing);
            return;
        }

        if (telegramId.equals(superAdminTelegramId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            sendText(bot, chatId, "✅ Welcome Super Admin! You have full platform access.");
            continueOnboarding(bot, chatId, superAdmin);
            return;
        }

        if (code == null || code.isBlank()) {
            sendText(bot, chatId, "❌ Invalid invite link. Please use the link provided by your admin.");
            return;
        }

        try {
            User newUser = inviteService.registerWithInvite(telegramId, code);
            TenantHelper.runWithTenant(newUser, () -> {
                String roleText = newUser.getRole() == com.bingo.app.master.enums.Role.ADMIN ? "Admin" : "Player";
                sendText(bot, chatId, "🎉 Welcome to BingoPlus! You have been registered as a " + roleText + ".");
                continueOnboarding(bot, chatId, newUser);
            });
        } catch (Exception e) {
            log.error("Registration failed for telegramId={}", telegramId, e);
            sendText(bot, chatId, "❌ Registration failed: " + e.getMessage());
        }
    }

    private void handleContact(Update update, OnboardingBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        var contact = update.getMessage().getContact();

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            sendText(bot, chatId, "Welcome to BingoPlus! To get started, open your invite link to register.");
            return;
        }

        if (contact.getPhoneNumber() != null
                && (contact.getUserId() == null || contact.getUserId().equals(telegramId))) {
            userService.savePhoneNumber(telegramId, contact.getPhoneNumber());
        }

        User fresh = userService.findByTelegramId(telegramId);
        if (fresh.getPhoneNumber() == null || fresh.getPhoneNumber().isBlank()) {
            sendText(bot, chatId, "⚠️ We couldn't read your phone number. Please tap the button below again:");
            requestPhoneNumber(bot, chatId);
            return;
        }

        sendText(bot, chatId, "✅ Phone number saved! Welcome to BingoPlus.");
        passwordOnboarding.prompt(bot, chatId, fresh);
    }

    private void handleText(Update update, OnboardingBot bot) {
        Long chatId = update.getMessage().getChatId();
        if (passwordOnboarding.isCapturing(bot, chatId)) {
            passwordOnboarding.handleText(bot, update);
            return;
        }
        sendText(bot, chatId, "Use the buttons below — there's nothing to type.");
    }

    private void continueOnboarding(OnboardingBot bot, Long chatId, User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            requestPhoneNumber(bot, chatId);
            return;
        }
        sendText(bot, chatId, "You are fully registered with phone " + user.getPhoneNumber() + ".");
        passwordOnboarding.prompt(bot, chatId, user);
    }

    public void requestPhoneNumber(OnboardingBot bot, Long chatId) {
        passwordOnboarding.requestPhoneNumber(bot, chatId,
                "📱 *One more step — verify your account*\n\n" +
                        "Please **share your phone number** by tapping the button below.\n\n" +
                        "You'll use this phone number to log into the mobile app.");
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
