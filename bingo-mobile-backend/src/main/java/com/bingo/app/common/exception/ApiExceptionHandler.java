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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            GameCreationException.class,
            GameProgressException.class,
            InviteRegistrationException.class,
            PlayerActionException.class
    })
    public ResponseEntity<Map<String, Object>> handleDomainException(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), userMessage(ex), domainCode(ex));
    }

    @ExceptionHandler(RequestAlreadyProcessedException.class)
    public ResponseEntity<Map<String, Object>> handleRequestAlreadyProcessed(RuntimeException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), userMessage(ex), "request_already_processed");
    }

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<Map<String, Object>> handleWalletException(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), userMessage(ex), "wallet");
    }

    @ExceptionHandler(AdminDeletionException.class)
    public ResponseEntity<Map<String, Object>> handleAdminDeletion(AdminDeletionException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getUserMessage(), ex.getCode());
    }

    @ExceptionHandler(TelegramAuthException.class)
    public ResponseEntity<Map<String, Object>> handleTelegramAuthException(TelegramAuthException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getUserMessage(), ex.getCode());
    }

    @ExceptionHandler(PhoneAuthException.class)
    public ResponseEntity<Map<String, Object>> handlePhoneAuthException(PhoneAuthException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "no_password" -> HttpStatus.valueOf(421);
            case "account_suspended", "account_pending" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNAUTHORIZED;
        };
        return buildResponse(status, ex.getMessage(), ex.getUserMessage(), ex.getCode());
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

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message, String userMessage, String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("userMessage", userMessage);
        body.put("code", code);
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
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
