package com.bingo.app.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for the error envelope, shared by
 * {@link ApiExceptionHandler}, the security filters and the Spring Security
 * entry points — everything that produces an error response must go through
 * here so the shape never drifts again.
 *
 * <pre>
 * {
 *   "success": false,
 *   "message": "Game not found",       // technical message
 *   "userMessage": "Game not found",   // copy safe to show to the end user
 *   "code": "not_found",               // stable code the client may localize
 *   "status": 404,
 *   "timestamp": "2026-10-08T12:00:00"
 * }
 * </pre>
 */
public final class ErrorResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ErrorResponses() {
    }

    public static Map<String, Object> body(HttpStatus status, String message, String userMessage, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("userMessage", userMessage);
        body.put("code", code);
        body.put("status", status.value());
        body.put("timestamp", LocalDateTime.now());
        return body;
    }

    /** Writes the envelope straight to the response — for filters and security handlers. */
    public static void write(HttpServletResponse response, HttpStatus status,
                             String message, String userMessage, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(body(status, message, userMessage, code)));
    }
}
