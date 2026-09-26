package com.bingo.app.bot.service;

import com.bingo.app.master.entity.User;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.BotConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    /**
     * Shows the role menu as a persistent reply keyboard. The keyboard stays on
     * screen, so users never need to type a command — pressing a button sends its
     * label back as a plain text message, which UpdateHandler routes to the same
     * action handlers as callback queries.
     */
    public void showMenu(BingoTelegramBot bot, Update update, User user) {
        Long chatId = update.getMessage() != null ?
                update.getMessage().getChatId() :
                update.getCallbackQuery().getMessage().getChatId();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        switch (user.getRole()) {
            case PLAYER -> {
                message.setText("\uD83C\uDFAE Welcome to BingoPlus!\n\nUse the buttons below to check your balance or active game. Tap the \uD83D\uDD34 *Open App* button next to the chat input to open the game.");
                keyboard.add(createRow(createButton(BotConstants.BTN_CHECK_BALANCE)));
                keyboard.add(createRow(createButton(BotConstants.BTN_ACTIVE_GAME)));
                keyboard.add(createRow(createButton(BotConstants.BTN_INVITE_FRIEND)));
            }
            case ADMIN -> {
                if (!user.isAdminApproved()) {
                    message.setText("⏳ *Account awaiting approval*\n\n" +
                            "Your admin account is pending super admin approval. You cannot generate " +
                            "player invite links or manage your room until the super admin approves " +
                            "your account. Please check back later.");
                    break;
                }
                message.setText("🎯 Admin Dashboard\n\nManage your games in the app (tap the \uD83D\uDD34 *Open App* button next to the chat input) or generate an invite link for players.");
                keyboard.add(createRow(createButton(BotConstants.BTN_INVITE_LINK)));
            }
            case SUPER_ADMIN -> {
                message.setText("\uD83D\uDC51 Super Admin Panel\n\nManage your platform in the app (tap the \uD83D\uDD34 *Open App* button next to the chat input) or generate an admin invite link.");
                keyboard.add(createRow(createButton(BotConstants.BTN_CREATE_ADMIN)));
            }
        }

        markup.setKeyboard(keyboard);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        markup.setInputFieldPlaceholder("Tap a menu button");
        message.setReplyMarkup(markup);
        message.setParseMode("Markdown");

        try {
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send menu: {}", e.getMessage());
        }
    }

    private KeyboardRow createRow(KeyboardButton... buttons) {
        KeyboardRow row = new KeyboardRow();
        for (KeyboardButton button : buttons) {
            row.add(button);
        }
        return row;
    }

    private KeyboardButton createButton(String text) {
        return KeyboardButton.builder()
                .text(text)
                .build();
    }
}