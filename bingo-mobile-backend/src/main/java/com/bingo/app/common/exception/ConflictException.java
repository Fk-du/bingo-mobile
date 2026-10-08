package com.bingo.app.common.exception;

/**
 * Thrown when the request conflicts with the current state of the resource
 * (duplicate action, already-called number, double submission).
 * Mapped to HTTP 409 by {@link ApiExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
