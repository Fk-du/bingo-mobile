package com.bingo.app.master.exception;

/**
 * Rejects a password-reset attempt that cannot proceed: the code is wrong,
 * expired, exhausted, or the new password is too weak. The {@code code} is
 * mapped to HTTP 400 in {@link com.bingo.app.common.exception.ApiExceptionHandler}
 * and drives the mobile app's error text.
 */
public class PasswordResetException extends RuntimeException {

    private static final int MAX_ATTEMPTS = 5;

    private final String userMessage;
    private final String code;

    public PasswordResetException(String message, String userMessage, String code) {
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

    public static PasswordResetException invalidCode() {
        return new PasswordResetException(
                "Invalid reset code",
                "That reset code is not correct. Check the code and try again.",
                "invalid_otp"
        );
    }

    public static PasswordResetException expired() {
        return new PasswordResetException(
                "Reset code expired",
                "That reset code has expired. Request a new code and try again.",
                "otp_expired"
        );
    }

    public static PasswordResetException tooManyAttempts() {
        return new PasswordResetException(
                "Too many wrong reset codes",
                "Too many wrong attempts. The reset code has been invalidated — request a new one.",
                "otp_max_attempts"
        );
    }

    public static PasswordResetException weakPassword(int minLength) {
        return new PasswordResetException(
                "Password too short",
                "The new password must be at least " + minLength + " characters long.",
                "weak_password"
        );
    }

    /** Wrong-entry ceiling before the code is burned regardless of expiry. */
    public static int maxAttempts() {
        return MAX_ATTEMPTS;
    }
}