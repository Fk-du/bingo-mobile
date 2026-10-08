package com.bingo.app.common.exception;

/**
 * Thrown when the request is syntactically valid but semantically wrong
 * (bad argument, wrong state for the operation). Mapped to HTTP 400 by
 * {@link ApiExceptionHandler}. The message is shown to the end user, so it
 * must be written as user-facing copy.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
