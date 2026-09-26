package com.bingo.app.bot.handler;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import com.bingo.app.bot.BotConstants;
import com.bingo.app.bot.callback.CallbackContext;
import com.bingo.app.bot.callback.CallbackRouter;
import com.bingo.app.bot.service.MenuService;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.command.StartCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateHandler {

    private final StartCommand startCommand;
    private final CallbackRouter callbackRouter;
    private final UserService userService;
    private final MenuService menuService;

    public void handle(Update update, BingoTelegramBot bot) {
        if (update == null) {
            log.warn("Received null update");
            return;
        }

        // Handle callback queries (button presses)
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update, bot);
            return;
        }

        // Handle text messages
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update, bot);
            return;
        }

        // Contact messages (phone share via request_contact button)
        if (update.hasMessage() && update.getMessage().hasContact()) {
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
            sendMessage(bot, chatId, "Welcome to BingoPlus! To get started, use the /start command with the invite link your admin provided.");
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
            startCommand.requestPhoneNumber(bot, chatId, "⚠️ We couldn't read your phone number. Please tap the button below again:");
            return;
        }

        sendMessage(bot, chatId, "✅ Phone number saved! Welcome to BingoPlus.");
        if (fresh.getTelegramUsername() == null || fresh.getTelegramUsername().isBlank()) {
            sendMessage(bot, chatId, "Tip: set a username in Telegram Settings so admins can identify you (Settings - Chat Settings - Username).");
        }
        TenantHelper.runWithTenant(fresh, () -> menuService.showMenu(bot, update, fresh));
    }

    private void handleCallbackQuery(Update update, BingoTelegramBot bot) {
        String data = update.getCallbackQuery().getData();
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        log.debug("Callback query received: data={}, telegramId={}", data, telegramId);

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            log.warn("User not found for telegramId: {}", telegramId);
            sendMessage(bot, chatId, "User not found. Please use /start to register.");
            return;
        }

        if (phoneMissing(user)) {
            startCommand.requestPhoneNumber(bot, chatId);
            return;
        }

        CallbackContext ctx = CallbackContext.builder()
                .bot(bot)
                .chatId(chatId)
                .telegramId(telegramId)
                .user(user)
                .data(data)
                .build();

        TenantHelper.runWithTenant(user, () -> callbackRouter.route(ctx));
    }

    private void handleTextMessage(Update update, BingoTelegramBot bot) {
        String text = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        log.debug("Text message received: text={}, telegramId={}", text, telegramId);

        // /start remains the only registration path (invite link deep link).
        if (text.startsWith("/start")) {
            startCommand.handle(update, bot);
            return;
        }

        if (text.equalsIgnoreCase("/app") || text.equalsIgnoreCase("/open") || text.equalsIgnoreCase("/launch")) {
            sendMessage(bot, chatId, "Open BingoPlus here: [BingoPlus](https://nowbingoplus.lol)");
            return;
        }

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            // Unknown user: guide them to register via /start with an invite.
            sendMessage(bot, chatId, "Welcome to BingoPlus! To get started, use the /start command with the invite link your admin provided.");
            return;
        }

        if (phoneMissing(user)) {
            startCommand.requestPhoneNumber(bot, chatId);
            return;
        }

        // Menu button pressed: the reply keyboard sends the button label as text.
        String action = BotConstants.BUTTON_ACTIONS.get(text);
        if (action != null) {
            CallbackContext ctx = CallbackContext.builder()
                    .bot(bot)
                    .chatId(chatId)
                    .telegramId(telegramId)
                    .user(user)
                    .data(action)
                    .build();
            TenantHelper.runWithTenant(user, () -> callbackRouter.route(ctx));
            return;
        }

        // Any other typed text (messages or commands): never acted on — just
        // re-show the menu so users interact with buttons instead.
        sendMessage(bot, chatId, "Use the menu buttons below — there's nothing to type.");
        TenantHelper.runWithTenant(user, () -> menuService.showMenu(bot, update, user));
    }

    private boolean phoneMissing(User user) {
        return user.getPhoneNumber() == null || user.getPhoneNumber().isBlank();
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build();
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}
