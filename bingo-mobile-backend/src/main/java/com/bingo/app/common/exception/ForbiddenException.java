package com.bingo.app.common.exception;

/**
 * Thrown when the caller is authenticated but not allowed to act on the
 * target (ownership mismatch, missing role, unapproved account).
 * Mapped to HTTP 403 by {@link ApiExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
