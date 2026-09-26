package com.bingo.app.infrastructure.security;

public class TelegramAuthException extends RuntimeException {

    private final String userMessage;
    private final String code;

    public TelegramAuthException(String message, String userMessage) {
        this(message, userMessage, null);
    }

    public TelegramAuthException(String message, String userMessage, String code) {
        super(message);
        this.userMessage = userMessage;
        this.code = code;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getCode() {
        return code;
    }
}
