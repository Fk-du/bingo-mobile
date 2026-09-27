package com.bingo.app.bot.onboarding;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "create a password" step that finishes registration, shared by every bot
 * so an admin or player ends up in the same state whichever one they came
 * through.
 *
 * <p>Mobile login is phone + password and the password cannot be derived from
 * Telegram, so a user who stops here can open the app but cannot sign in. The
 * step is offered once the phone number is known and is skipped silently for
 * anyone who already has a password.</p>
 *
 * <p>Capture state is in-memory and keyed by bot + chat. A restart mid-capture
 * drops the half-typed password, which is the safe outcome; the user is simply
 * offered the panel again next time.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordOnboardingService {

    private static final int MIN_PASSWORD_LENGTH = 4;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    private final Map<String, PendingPassword> pendingPasswords = new ConcurrentHashMap<>();

    /** True while this chat is typing a password, so the bot must not answer with its menu. */
    public boolean isCapturing(TelegramLongPollingBot bot, Long chatId) {
        return pendingPasswords.containsKey(stateKey(bot, chatId));
    }

    /**
     * Offer to create a password, or report that one already exists. Call this
     * once the phone number has been shared.
     */
    public void prompt(TelegramLongPollingBot bot, Long chatId, User user) {
        if (hasPassword(user)) {
            sendText(bot, chatId, "🔑 You already have a password for the mobile app — log in with your phone number and it.");
            return;
        }
        sendText(bot, chatId,
                "📱 *To use the BingoPlus mobile app*\n\n"
                        + "Mobile login is your phone number plus a password, so set a password once here "
                        + "and you can sign in from the app. You can skip this and set it later.",
                inlinePanel());
    }

    /**
     * Consume a typed message as password input.
     *
     * @return true when the message was part of the capture and has been
     *         handled, so the caller must not treat it as menu input.
     */
    public boolean handleText(TelegramLongPollingBot bot, Update update) {
        Long chatId = update.getMessage().getChatId();
        String stateKey = stateKey(bot, chatId);
        PendingPassword pending = pendingPasswords.get(stateKey);
        if (pending == null) {
            return false;
        }

        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText() == null ? "" : update.getMessage().getText().trim();
        int messageId = update.getMessage().getMessageId();

        if (!pending.isConfirmed()) {
            if (text.length() < MIN_PASSWORD_LENGTH) {
                deleteMessage(bot, chatId, messageId);
                sendText(bot, chatId, "⚠️ Password must be at least " + MIN_PASSWORD_LENGTH + " characters. Please try again:");
                return true;
            }
            deleteMessage(bot, chatId, messageId);
            pendingPasswords.put(stateKey, new PendingPassword(text, true));
            sendText(bot, chatId, "🔁 Please type the same password again to confirm:");
            return true;
        }

        deleteMessage(bot, chatId, messageId);
        if (!pending.getPassword().equals(text)) {
            pendingPasswords.put(stateKey, new PendingPassword(null, false));
            sendText(bot, chatId, "❌ The passwords did not match. Type your new password (at least "
                    + MIN_PASSWORD_LENGTH + " characters):");
            return true;
        }

        pendingPasswords.remove(stateKey);
        try {
            User user = userService.findByTelegramId(telegramId);
            if (user == null) {
                sendText(bot, chatId, "Account not found. Please /start with your invite link.");
                return true;
            }
            userService.setPassword(user.getId(), passwordEncoder.encode(text));
            sendText(bot, chatId, "✅ Password saved! You can now log into the BingoPlus mobile app "
                    + "with your phone number and this password.");
        } catch (Exception e) {
            log.error("Failed to set password for telegramId={}", telegramId, e);
            sendText(bot, chatId, "❌ Failed to save password. Please try again.");
        }
        return true;
    }

    /**
     * Handle the "Create Password" / "Skip" buttons.
     *
     * @return true when the callback belonged to this step.
     */
    public boolean handleCallback(TelegramLongPollingBot bot, Update update) {
        String data = update.getCallbackQuery().getData();
        if (!OnboardingBotConstants.Actions.CREATE_PASSWORD.equals(data)
                && !OnboardingBotConstants.Actions.SKIP_PASSWORD.equals(data)) {
            return false;
        }

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long telegramId = update.getCallbackQuery().getFrom().getId();

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            sendText(bot, chatId, "Account not found. Please /start with your invite link.");
            return true;
        }

        if (OnboardingBotConstants.Actions.CREATE_PASSWORD.equals(data)) {
            pendingPasswords.put(stateKey(bot, chatId), new PendingPassword(null, false));
            sendText(bot, chatId, "🔐 Type your new password (at least " + MIN_PASSWORD_LENGTH + " characters):");
            return true;
        }

        pendingPasswords.remove(stateKey(bot, chatId));
        sendText(bot, chatId, "No problem! You can set a password any time from this bot "
                + "if you want to use the mobile app.");
        return true;
    }

    /** Drop any half-typed password for this chat, e.g. when a new invite link is opened. */
    public void reset(TelegramLongPollingBot bot, Long chatId) {
        pendingPasswords.remove(stateKey(bot, chatId));
    }

    private boolean hasPassword(User user) {
        return user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
    }

    private String stateKey(TelegramLongPollingBot bot, Long chatId) {
        return bot.getBotUsername() + ":" + chatId;
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

    /** The share-phone keyboard, shared so both bots word the same request. */
    public void requestPhoneNumber(TelegramLongPollingBot bot, Long chatId, String text) {
        KeyboardButton button = KeyboardButton.builder()
                .text(OnboardingBotConstants.BTN_SHARE_PHONE)
                .requestContact(true)
                .build();
        KeyboardRow row = new KeyboardRow();
        row.add(button);
        ReplyKeyboardMarkup markup = ReplyKeyboardMarkup.builder()
                .keyboardRow(row)
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(markup)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Failed to request phone number: {}", e.getMessage());
        }
    }

    private void sendText(TelegramLongPollingBot bot, Long chatId, String text) {
        sendText(bot, chatId, text, null);
    }

    private void sendText(TelegramLongPollingBot bot, Long chatId, String text, InlineKeyboardMarkup markup) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(markup)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }

    /** The password message is deleted after reading, so it is not left in the chat history. */
    private void deleteMessage(TelegramLongPollingBot bot, Long chatId, int messageId) {
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
