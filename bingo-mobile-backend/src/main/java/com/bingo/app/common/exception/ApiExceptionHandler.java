package com.bingo.app.common.exception;

import com.bingo.app.master.exception.AdminDeletionException;
import com.bingo.app.master.exception.InviteRegistrationException;
import com.bingo.app.tenant.exception.GameCreationException;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.exception.PlayerActionException;
import com.bingo.app.tenant.exception.RequestAlreadyProcessedException;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.infrastructure.security.TelegramAuthException;
import com.bingo.app.infrastructure.security.PhoneAuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns every exception into the shared error envelope
 * ({@link ErrorResponses#body}). Anything not matched below falls through to
 * {@link #handleUnexpected}, which returns a generic 500 — internal messages
 * and stack traces are logged, never sent to the client.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    // ------------------------------------------------------------------
    // Domain exceptions
    // ------------------------------------------------------------------

    @ExceptionHandler({
            GameCreationException.class,
            GameProgressException.class,
            InviteRegistrationException.class,
            PlayerActionException.class
    })
    public ResponseEntity<Map<String, Object>> handleDomainException(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), userMessage(ex), domainCode(ex));
    }

    @ExceptionHandler(RequestAlreadyProcessedException.class)
    public ResponseEntity<Map<String, Object>> handleRequestAlreadyProcessed(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), userMessage(ex), "request_already_processed");
    }

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<Map<String, Object>> handleWalletException(WalletException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getUserMessage(), "wallet");
    }

    @ExceptionHandler(AdminDeletionException.class)
    public ResponseEntity<Map<String, Object>> handleAdminDeletion(AdminDeletionException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getUserMessage(), ex.getCode());
    }

    @ExceptionHandler(TelegramAuthException.class)
    public ResponseEntity<Map<String, Object>> handleTelegramAuthException(TelegramAuthException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getUserMessage(), ex.getCode());
    }

    @ExceptionHandler(PhoneAuthException.class)
    public ResponseEntity<Map<String, Object>> handlePhoneAuthException(PhoneAuthException ex) {
        HttpStatus status = switch (ex.getCode() == null ? "" : ex.getCode()) {
            case "no_password" -> HttpStatus.valueOf(421);
            case "account_suspended", "account_pending" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNAUTHORIZED;
        };
        return build(status, ex.getMessage(), ex.getUserMessage(), ex.getCode());
    }

    // ------------------------------------------------------------------
    // Common API exceptions
    // ------------------------------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getMessage(), "not_found");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getMessage(), "bad_request");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getMessage(), "bad_request");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), ex.getMessage(), "forbidden");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getMessage(), "conflict");
    }

    // ------------------------------------------------------------------
    // Request problems (bad JSON, bad params, failed bean validation)
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Validation failed: " + fields, fields, "validation_error");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String fields = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "Validation failed: " + fields, fields, "validation_error");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body",
                "The request could not be read. Check the payload and try again.", "malformed_request");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, Object>> handleMissingParameter(Exception ex) {
        String name = ex instanceof MissingServletRequestParameterException param
                ? param.getParameterName()
                : ((MissingServletRequestPartException) ex).getRequestPartName();
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter: " + name,
                "Missing required field: " + name, "missing_parameter");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "Parameter '" + ex.getName() + "' has an invalid value";
        return build(HttpStatus.BAD_REQUEST, detail, detail, "type_mismatch");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String detail = "HTTP method " + ex.getMethod() + " is not supported for this endpoint";
        return build(HttpStatus.METHOD_NOT_ALLOWED, detail, detail, "method_not_allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type",
                "Unsupported content type. Send application/json.", "unsupported_media_type");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is too large",
                "The uploaded file is too large.", "payload_too_large");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNoHandler(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "Endpoint not found", "Endpoint not found.", "not_found");
    }

    // ------------------------------------------------------------------
    // Unexpected — generic 500, details only go to the log
    // ------------------------------------------------------------------

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Database operation failed",
                genericUserMessage(), "database_error");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error",
                genericUserMessage(), "internal_error");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String genericUserMessage() {
        return "Something went wrong. Please try again.";
    }

    private String domainCode(RuntimeException ex) {
        if (ex instanceof InviteRegistrationException invite) {
            return invite.getCode() != null ? invite.getCode() : "invite_registration";
        }
        if (ex instanceof GameCreationException) {
            return "game_creation";
        }
        if (ex instanceof GameProgressException) {
            return "game_progress";
        }
        return "player_action";
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message,
                                                      String userMessage, String code) {
        return ResponseEntity.status(status).body(ErrorResponses.body(status, message, userMessage, code));
    }

    private String userMessage(RuntimeException ex) {
        if (ex instanceof GameCreationException gameCreationException) {
            return gameCreationException.getUserMessage();
        }
        if (ex instanceof GameProgressException gameProgressException) {
            return gameProgressException.getUserMessage();
        }
        if (ex instanceof InviteRegistrationException inviteRegistrationException) {
            return inviteRegistrationException.getUserMessage();
        }
        if (ex instanceof AdminDeletionException adminDeletionException) {
            return adminDeletionException.getUserMessage();
        }
        if (ex instanceof PlayerActionException playerActionException) {
            return playerActionException.getUserMessage();
        }
        if (ex instanceof WalletException walletException) {
            return walletException.getUserMessage();
        }
        return ex.getMessage();
    }
}
