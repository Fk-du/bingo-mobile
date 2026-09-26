package com.bingo.app.bot.onboarding;

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
 * Registers the registration-only bot for long polling. The bot bean is only
 * created when {@code app.telegram.registration-bot.token} is configured, so
 * this config is a no-op for deployments that don't run mobile onboarding.
 */
@Configuration
@ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${app.telegram.registration-bot.token:}')")
@RequiredArgsConstructor
@Slf4j
public class OnboardingBotRegistrationConfig {

    private final OnboardingBot onboardingBot;

    @PostConstruct
    public void registerBot() {
        try {
            try {
                onboardingBot.execute(SetWebhook.builder().url("").build());
            } catch (Exception ignored) {
            }
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(onboardingBot);
            log.info("Registration bot registered successfully: @{}", onboardingBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register registration bot: {}", e.getMessage());
        }
    }
}