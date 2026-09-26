package com.bingo.app.bot.config;

import com.bingo.app.bot.BingoTelegramBot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Registers the main bot as a long-polling receiver. Disabled entirely when no
 * {@code app.telegram.bot.token} is configured, so the mobile-app backend (which
 * authenticates via phone+password JWT, never Telegram) starts without one.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${app.telegram.bot.token:}')")
public class TelegramBotRegistrationConfig {

    private final BingoTelegramBot bingoTelegramBot;

    @PostConstruct
    public void registerBot() {
        try {
            // Clear any existing webhook before registering as a long-polling bot
            try {
                bingoTelegramBot.execute(SetWebhook.builder().url("").build());
            } catch (Exception ignored) {
                // Old webhook may not exist — ignore
            }

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bingoTelegramBot);
            log.info("Telegram bot registered successfully: @{}", bingoTelegramBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register bot: {}", e.getMessage());
            throw new IllegalStateException("Failed to register Telegram bot", e);
        }
    }
}