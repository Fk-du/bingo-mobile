package com.bingo.app.infrastructure.security;

import com.bingo.app.common.exception.ErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes the shared error envelope for failures that happen in the Spring
 * Security filter chain — i.e. before any controller runs, so
 * {@code ApiExceptionHandler} never sees them. Without these, unauthenticated
 * or unauthorized requests got a bare status line with an empty body.
 */
public final class SecurityErrorHandlers {

    private SecurityErrorHandlers() {
    }

    /** 401 — no (valid) credentials. */
    public static class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws java.io.IOException {
            ErrorResponses.write(response, HttpStatus.UNAUTHORIZED,
                    "Authentication required",
                    "Your session is missing or has expired. Please log in again.",
                    "unauthenticated");
        }
    }

    /** 403 — authenticated, but not allowed (e.g. {@code @PreAuthorize} denied). */
    public static class JsonAccessDeniedHandler implements AccessDeniedHandler {
        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws java.io.IOException {
            ErrorResponses.write(response, HttpStatus.FORBIDDEN,
                    "Access denied",
                    "You do not have permission to perform this action.",
                    "access_denied");
        }
    }
}
