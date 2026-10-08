package com.bingo.app.common.exception;

/**
 * Thrown when a requested entity does not exist (or the caller is not allowed
 * to know that it does). Mapped to HTTP 404 by {@link ApiExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
