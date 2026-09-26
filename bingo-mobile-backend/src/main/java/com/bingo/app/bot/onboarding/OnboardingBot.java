package com.bingo.app.bot.onboarding;

import com.bingo.app.bot.onboarding.handler.OnboardingUpdateHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Registration-only bot for the mobile app. Onboards players/admins via an
 * invite code: register -> share phone -> optionally create a password for
 * mobile login. No game menu, no game commands.
 *
 * Only loaded when {@code app.telegram.registration-bot.token} is configured,
 * so the game bot's deployment is unaffected when it is absent.
 */
@Component
@ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${app.telegram.registration-bot.token:}')")
@RequiredArgsConstructor
@Slf4j
public class OnboardingBot extends TelegramLongPollingBot {

    private final OnboardingUpdateHandler updateHandler;

    @Value("${app.telegram.registration-bot.username}")
    private String username;

    @Value("${app.telegram.registration-bot.token}")
    private String token;

    private Long botId;

    @PostConstruct
    public void init() {
        log.info("Initializing registration-only bot: @{}", username);
        try {
            var me = execute(new GetMe());
            botId = me.getId();
            log.info("Registration bot connected: id={}, username=@{}", me.getId(), me.getUserName());
        } catch (TelegramApiException e) {
            log.error("Failed to connect registration bot: {}", e.getMessage());
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