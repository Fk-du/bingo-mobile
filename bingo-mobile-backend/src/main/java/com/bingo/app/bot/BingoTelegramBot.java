package com.bingo.app.bot;

import com.bingo.app.bot.handler.UpdateHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonDefault;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("deprecation")
public class BingoTelegramBot extends TelegramLongPollingBot {

    private final UpdateHandler updateHandler;

    @Value("${app.telegram.bot.username}")
    private String username;

    @Value("${app.telegram.bot.token}")
    private String token;

    @Value("${bingo.webapp.url}")
    private String webAppUrl;

    /**
     * Whether the chat menu button opens the web app as a Telegram Mini App.
     * Off by default: the game is played in the mobile app, and a Mini App
     * button that points at a URL nothing serves is worse than no button.
     */
    @Value("${bingo.telegram.bot.webapp-button-enabled:false}")
    private boolean webAppButtonEnabled;

    private Long botId;

    @PostConstruct
    public void init() {
        log.info("Initializing Telegram Bot: @{}", username);
        log.info("Bot token configured: {}", token != null ? "YES (length: " + token.length() + ")" : "NO");

        if (token == null || token.isEmpty() || token.equals("your_bot_token")) {
            // The mobile-app backend runs without a bot token (phone+password auth
            // only); its Telegram duties are handled by the Mini App backend. Skip
            // connection instead of failing startup.
            log.warn("No BOT_TOKEN configured — skipping Telegram bot connection");
            return;
        }

        try {
            var me = execute(new GetMe());
            botId = me.getId();
            log.info("Bot connected: id={}, username=@{}", me.getId(), me.getUserName());
        } catch (TelegramApiException e) {
            log.error("Failed to connect bot: {}", e.getMessage());
            log.error("Please verify your BOT_TOKEN is correct");
        }

        setMenuButton();
    }

    private void setMenuButton() {
        try {
            if (webAppButtonEnabled) {
                execute(SetChatMenuButton.builder()
                        .menuButton(MenuButtonWebApp.builder()
                                .text("Open App")
                                .webAppInfo(new WebAppInfo(webAppUrl))
                                .build())
                        .build());
                log.info("Bot menu button set to WebApp: {}", webAppUrl);
                return;
            }
            // Not simply skipping: a chat menu button already handed out stays in
            // the user's chat until it is replaced, so an existing "Open App"
            // button pointing at a dead Mini App has to be reset explicitly.
            execute(SetChatMenuButton.builder()
                    .menuButton(MenuButtonDefault.builder().build())
                    .build());
            log.info("Bot menu button reset to default (web app button disabled)");
        } catch (TelegramApiException e) {
            log.error("Failed to set menu button: {}", e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateHandler.handle(update, this);
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    public Long getBotId() {
        return botId;
    }
}
