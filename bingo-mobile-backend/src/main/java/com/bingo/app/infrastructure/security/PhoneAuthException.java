package com.bingo.app.infrastructure.security;

/**
 * Thrown when phone+password authentication fails. The {@code code} maps to a
 * specific HTTP response: {@code no_password} → 421, {@code invalid_credentials}
 * → 401, {@code account_suspended} → 403, {@code account_pending} → 403.
 */
public class PhoneAuthException extends RuntimeException {

    private final String userMessage;
    private final String code;

    public PhoneAuthException(String message, String userMessage, String code) {
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

    public static PhoneAuthException invalidCredentials() {
        return new PhoneAuthException(
                "Invalid phone or password",
                "Incorrect phone number or password.",
                "invalid_credentials"
        );
    }

    public static PhoneAuthException noPassword() {
        return new PhoneAuthException(
                "No password set",
                "No password is set for this account yet. Open the Telegram bot to create one.",
                "no_password"
        );
    }

    public static PhoneAuthException suspended() {
        return new PhoneAuthException(
                "Account is suspended",
                "Your account has been suspended. Contact the platform owner for details.",
                "account_suspended"
        );
    }
}