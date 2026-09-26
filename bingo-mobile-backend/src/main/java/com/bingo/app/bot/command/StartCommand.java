package com.bingo.app.bot.command;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.bot.BotConstants;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.service.MenuService;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartCommand {

    private final UserService userService;
    private final InviteService inviteService;
    private final MenuService menuService;

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    public void handle(Update update, BingoTelegramBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();
        String code = extractStartCode(text);
        Long chatId = update.getMessage().getChatId();

        log.info("Start command from telegramId={}, code={}", telegramId, code);

        // Check if user exists
        User existingUser = userService.findByTelegramId(telegramId);

        if (existingUser != null) {
            // PLAYER with an invite code → try to upgrade to ADMIN
            if (code != null && !code.isBlank() && existingUser.getRole() == Role.PLAYER) {
                try {
                    User upgraded = inviteService.registerWithInvite(telegramId, code);
                    TenantHelper.runWithTenant(upgraded, () -> {
                        sendMessage(bot, chatId, "🎉 Welcome to BingoPlus! You have been registered as an Admin.");
                        continueToMenu(bot, update, upgraded);
                    });
                    return;
                } catch (Exception e) {
                    log.warn("Invite upgrade failed for telegramId={}, falling back", telegramId, e);
                }
            }

            TenantHelper.runWithTenant(existingUser, () -> {
                sendMessage(bot, chatId, "👋 Welcome back!");
                continueToMenu(bot, update, existingUser);
            });
            return;
        }

        // Super admin registration
        if (telegramId.equals(superAdminTelegramId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            TenantHelper.runWithTenant(superAdmin, () -> {
                sendMessage(bot, chatId, "✅ Welcome Super Admin! You have full platform access.");
                continueToMenu(bot, update, superAdmin);
            });
            return;
        }

        // New user with invite code
        if (code == null || code.isBlank()) {
            sendMessage(bot, chatId, "❌ Invalid invite link. Please use the link provided by your admin.");
            return;
        }

        try {
            User newUser = inviteService.registerWithInvite(telegramId, code);
            TenantHelper.runWithTenant(newUser, () -> {
                String roleText = newUser.getRole() == Role.ADMIN ? "Admin" : "Player";
                sendMessage(bot, chatId, "🎉 Welcome to BingoPlus! You have been registered as a " + roleText + ".");
                continueToMenu(bot, update, newUser);
            });
        } catch (Exception e) {
            log.error("Registration failed for telegramId={}", telegramId, e);
            sendMessage(bot, chatId, "❌ Registration failed: " + e.getMessage());
        }
    }

    /**
     * Phone number and username are the verified identity of every user, so the
     * menu is withheld until the phone number is shared. Existing users who
     * already shared it skip straight through.
     */
    private void continueToMenu(BingoTelegramBot bot, Update update, User user) {
        Long chatId = update.getMessage().getChatId();
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            requestPhoneNumber(bot, chatId);
            return;
        }
        if (user.getTelegramUsername() == null || user.getTelegramUsername().isBlank()) {
            sendMessage(bot, chatId, "Tip: set a username in Telegram Settings so admins can identify you (Settings - Chat Settings - Username).");
        }
        menuService.showMenu(bot, update, user);
    }

    public void requestPhoneNumber(BingoTelegramBot bot, Long chatId) {
        requestPhoneNumber(bot, chatId, "📱 *One more step — verify your account*\n\n" +
                "To keep the room secure and show who you are, please **share your phone number** by tapping the button below.\n\n" +
                "A username also helps admins recognize you — if you don't have one yet, set it in Telegram: *Settings → Username*.");
    }

    public void requestPhoneNumber(BingoTelegramBot bot, Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(requestPhoneMarkup())
                .parseMode("Markdown")
                .build();
        try {
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to request phone number: {}", e.getMessage());
        }
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup requestPhoneMarkup() {
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton button =
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton.builder()
                        .text(BotConstants.BTN_SHARE_PHONE)
                        .requestContact(true)
                        .build();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow row =
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow();
        row.add(button);
        org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup markup =
                org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup.builder()
                        .keyboardRow(row)
                        .resizeKeyboard(true)
                        .oneTimeKeyboard(true)
                        .build();
        return markup;
    }

    private String extractStartCode(String text) {
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build();
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }
}
