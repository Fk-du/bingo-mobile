package com.bingo.app.bot.handler;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.command.StartCommand;
import com.bingo.app.bot.i18n.BotText;
import com.bingo.app.bot.onboarding.PasswordOnboardingService;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Registration only.
 *
 * <p>The bot exists to get a person from an invite link to a mobile login:
 * register with the invite code, share a phone number, set a password. There is
 * no game menu, no balance, no game list and no dashboard — the game itself
 * lives in the mobile app, and every word the bot sends is the shared
 * bilingual catalogue in {@link BotText}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateHandler {

    private final StartCommand startCommand;
    private final UserService userService;
    private final PasswordOnboardingService passwordOnboarding;
    private final BotText text;

    public void handle(Update update, BingoTelegramBot bot) {
        if (update == null) {
            log.warn("Received null update");
            return;
        }

        // The password panel is part of registration and must be answered first.
        if (update.hasCallbackQuery()) {
            passwordOnboarding.handleCallback(bot, update);
            return;
        }

        if (update.getMessage() != null && update.getMessage().hasText()) {
            handleTextMessage(update, bot);
            return;
        }

        if (update.getMessage() != null && update.getMessage().hasContact()) {
            handleContact(update, bot);
        }
    }

    private void handleContact(Update update, BingoTelegramBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();
        String firstName = update.getMessage().getFrom().getFirstName();
        String lastName = update.getMessage().getFrom().getLastName();

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            sendMessage(bot, chatId, text.t("unknown_user_need_invite"));
            return;
        }

        userService.mergeTelegramProfile(telegramId, username, firstName, lastName);

        var contact = update.getMessage().getContact();
        if (contact.getPhoneNumber() != null
                && (contact.getUserId() == null || contact.getUserId().equals(telegramId))) {
            userService.savePhoneNumber(telegramId, contact.getPhoneNumber());
        }

        User fresh = userService.findByTelegramId(telegramId);
        if (fresh.getPhoneNumber() == null || fresh.getPhoneNumber().isBlank()) {
            passwordOnboarding.requestPhoneNumber(bot, chatId, text.t("phone_request_full"));
            return;
        }

        sendMessage(bot, chatId, text.t("phone_saved"));
        if (fresh.getTelegramUsername() == null || fresh.getTelegramUsername().isBlank()) {
            sendMessage(bot, chatId, text.t("username_tip"));
        }
        // Mobile login is phone + password and the password cannot be derived
        // from Telegram, so the last registration step is offered here.
        passwordOnboarding.prompt(bot, chatId, fresh);
    }

    private void handleTextMessage(Update update, BingoTelegramBot bot) {
        String message = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        log.debug("Text message received: text={}, telegramId={}", message, telegramId);

        // /start is the only registration path (invite link deep link).
        if (message.startsWith("/start")) {
            startCommand.handle(update, bot);
            return;
        }

        // A half-typed password is never chat input — answer it as a password.
        if (passwordOnboarding.isCapturing(bot, chatId)) {
            passwordOnboarding.handleText(bot, update);
            return;
        }

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            sendMessage(bot, chatId, text.t("unknown_user_need_invite"));
            return;
        }
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            passwordOnboarding.requestPhoneNumber(bot, chatId, text.t("phone_request_full"));
            return;
        }

        // Nothing else is a command: point back to the registration steps
        // instead of answering text that is not part of them.
        sendMessage(bot, chatId, text.t("nothing_to_type"));
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String body) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(body)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}
