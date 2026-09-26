package com.bingo.app.bot.onboarding.handler;

import com.bingo.app.bot.onboarding.OnboardingBot;
import com.bingo.app.bot.onboarding.OnboardingBotConstants;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal flow for the registration-only bot:
 * <ol>
 *   <li>{@code /start <inviteCode>} — register via {@link InviteService} (same role/tenant logic as the game bot).</li>
 *   <li>Share phone via {@code request_contact} — {@link UserService#savePhoneNumber}.</li>
 *   <li>Optional "Create Password" — set + confirm a password for mobile-app login.</li>
 * </ol>
 * No game menu; the game bot and web app are untouched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OnboardingUpdateHandler {

    private final InviteService inviteService;
    private final UserService userService;
    private final MasterMapper masterMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    /** In-memory 2-step password state, keyed by (botId, chatId). */
    private final Map<String, PendingPassword> passwordState = new ConcurrentHashMap<>();

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
            handleCallback(update, bot);
        }
    }

    private void handleStart(Update update, OnboardingBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String code = extractStartCode(update.getMessage().getText());
        passwordState.remove(stateKey(bot, chatId));

        User existing = userService.findByTelegramId(telegramId);
        if (existing != null) {
            sendText(bot, chatId, "👋 Welcome back! You are already registered.");
            continueOnboarding(bot, update, existing);
            return;
        }

        if (telegramId.equals(superAdminTelegramId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            sendText(bot, chatId, "✅ Welcome Super Admin! You have full platform access.");
            continueOnboarding(bot, update, superAdmin);
            return;
        }

        if (code == null || code.isBlank()) {
            sendText(bot, chatId, "❌ Invalid invite link. Please use the link provided by your admin.");
            return;
        }

        try {
            User newUser = inviteService.registerWithInvite(telegramId, code);
            TenantHelper.runWithTenant(newUser, () -> {
                String roleText = newUser.getRole() == Role.ADMIN ? "Admin" : "Player";
                sendText(bot, chatId, "🎉 Welcome to BingoPlus! You have been registered as a " + roleText + ".");
                continueOnboarding(bot, update, newUser);
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
        promptPassword(bot, chatId, fresh);
    }

    private void handleText(Update update, OnboardingBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String stateKey = stateKey(bot, chatId);
        PendingPassword pending = passwordState.get(stateKey);

        if (pending == null) {
            sendText(bot, chatId, "Use the buttons below — there's nothing to type.");
            return;
        }

        String text = update.getMessage().getText() == null ? "" : update.getMessage().getText().trim();
        int messageId = update.getMessage().getMessageId();

        if (!pending.isConfirmed()) {
            if (text.length() < 4) {
                deleteMessage(bot, chatId, messageId);
                sendText(bot, chatId, "⚠️ Password must be at least 4 characters. Please try again:");
                return;
            }
            deleteMessage(bot, chatId, messageId);
            passwordState.put(stateKey, new PendingPassword(text, true));
            sendText(bot, chatId, "🔁 Please type the same password again to confirm:");
            return;
        }

        deleteMessage(bot, chatId, messageId);
        if (!pending.getPassword().equals(text)) {
            sendText(bot, chatId, "❌ The passwords did not match. Please start again:");
            passwordState.put(stateKey, new PendingPassword(null, false));
            sendText(bot, chatId, "🔐 Type your new password (at least 4 characters):");
            return;
        }

        passwordState.remove(stateKey);
        try {
            User user = userService.findByTelegramId(telegramId);
            if (user == null) {
                sendText(bot, chatId, "Account not found. Please /start with your invite link.");
                return;
            }
            userService.setPassword(user.getId(), passwordEncoder.encode(text));
            sendText(bot, chatId, "✅ Password saved! You can now log into the BingoPlus mobile app with your phone number and password.");
        } catch (Exception e) {
            log.error("Failed to set password for telegramId={}", telegramId, e);
            sendText(bot, chatId, "❌ Failed to save password. Please try again.");
        }
    }

    private void handleCallback(Update update, OnboardingBot bot) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String data = update.getCallbackQuery().getData();
        String stateKey = stateKey(bot, chatId);

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            sendText(bot, chatId, "Account not found. Please /start with your invite link.");
            return;
        }

        if (OnboardingBotConstants.Actions.CREATE_PASSWORD.equals(data)) {
            passwordState.put(stateKey, new PendingPassword(null, false));
            sendText(bot, chatId, "🔐 Type your new password (at least 4 characters):");
            return;
        }

        if (OnboardingBotConstants.Actions.SKIP_PASSWORD.equals(data)) {
            passwordState.remove(stateKey);
            sendText(bot, chatId, "No problem! You can set a password any time from this bot if you want to use the mobile app.");
        }
    }

    private void continueOnboarding(OnboardingBot bot, Update update, User user) {
        Long chatId = update.getMessage().getChatId();
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            requestPhoneNumber(bot, chatId);
            return;
        }
        sendText(bot, chatId, "You are fully registered with phone " + user.getPhoneNumber() + ".");
        promptPassword(bot, chatId, user);
    }

    private void promptPassword(OnboardingBot bot, Long chatId, User user) {
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        if (hasPassword) {
            sendText(bot, chatId, "🔑 You already have a password for the mobile app — just log in with your phone number.");
            return;
        }
        sendText(bot, chatId,
                "📱 *OPTIONAL — use the mobile app*\n\n" +
                        "If you want to play in the BingoPlus mobile app, create a password once. " +
                        "You'll then log in with your phone number and this password.", 
                inlinePanel());
    }

    private InlineKeyboardMarkup inlinePanel() {
        InlineKeyboardButton create = InlineKeyboardButton.builder()
                .text(OnboardingBotConstants.BTN_CREATE_PASSWORD)
                .callbackData(OnboardingBotConstants.Actions.CREATE_PASSWORD)
                .build();
        InlineKeyboardButton skip = InlineKeyboardButton.builder()
                .text("Skip for now")
                .callbackData(OnboardingBotConstants.Actions.SKIP_PASSWORD)
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(create), List.of(skip)))
                .build();
    }

    public void requestPhoneNumber(OnboardingBot bot, Long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text("📱 *One more step — verify your account*\n\n" +
                        "Please **share your phone number** by tapping the button below.\n\n" +
                        "You'll use this phone number to log into the mobile app.")
                .replyMarkup(requestPhoneMarkup())
                .parseMode("Markdown")
                .build();
        try {
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to request phone number: {}", e.getMessage());
        }
    }

    private ReplyKeyboardMarkup requestPhoneMarkup() {
        KeyboardButton button = KeyboardButton.builder()
                .text(OnboardingBotConstants.BTN_SHARE_PHONE)
                .requestContact(true)
                .build();
        KeyboardRow row = new KeyboardRow();
        row.add(button);
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(row)
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
    }

    private String extractStartCode(String text) {
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    private String stateKey(OnboardingBot bot, Long chatId) {
        return bot.getBotUsername() + ":" + chatId;
    }

    private void sendText(OnboardingBot bot, Long chatId, String text) {
        sendText(bot, chatId, text, null);
    }

    private void sendText(OnboardingBot bot, Long chatId, String text, InlineKeyboardMarkup markup) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(markup)
                    .build();
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }

    private void deleteMessage(OnboardingBot bot, Long chatId, int messageId) {
        try {
            bot.execute(DeleteMessage.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete password message in chat {}: {}", chatId, e.getMessage());
        }
    }

    /** Two-step password capture: first entry, then confirmation. */
    private static final class PendingPassword {
        private final String password;
        private final boolean confirmed;

        PendingPassword(String password, boolean confirmed) {
            this.password = password;
            this.confirmed = confirmed;
        }

        String getPassword() {
            return password;
        }

        boolean isConfirmed() {
            return confirmed;
        }
    }
}