package com.bingo.app.infrastructure.security;

import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Single authentication filter for every deployment. Accepts either:
 * <ul>
 *   <li>{@code Authorization: Bearer <jwt>} — mobile-app (phone+password) tokens, or</li>
 *   <li>{@code Authorization: tma <initData>} — Telegram WebApp signed init data (web app).</li>
 * </ul>
 * Both paths resolve to the same master {@link User} and produce an identical
 * {@link UserPrincipal}, so controllers/services never know which method was used.
 */
@Component
@Slf4j
public class TokenAuthFilter extends OncePerRequestFilter {

    private final TelegramAuthService telegramAuthService;
    private final JwtTokenService jwtTokenService;
    private final UserService userService;

    public TokenAuthFilter(TelegramAuthService telegramAuthService,
                           JwtTokenService jwtTokenService,
                           UserService userService) {
        this.telegramAuthService = telegramAuthService;
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            User user = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                user = authenticateJwt(authHeader.substring(7));
            } else if (authHeader != null && authHeader.startsWith("tma ")) {
                try {
                    user = telegramAuthService.authenticate(authHeader.substring(4));
                } catch (Exception e) {
                    log.error("Telegram authentication failed: {}", e.getMessage());
                }
            }

            authenticateUser(request, response, user);
        } finally {
            TenantHelper.clear();
        }
        chain.doFilter(request, response);
    }

    private User authenticateJwt(String token) {
        Long userId = jwtTokenService.parseSubject(token);
        if (userId == null) {
            log.warn("JWT authentication failed: invalid or expired token");
            return null;
        }
        return userService.findByIdEntity(userId);
    }

    private void authenticateUser(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        if (user == null) {
            return;
        }

        // Enforce account status at the request boundary. This project
        // authenticates via a stateless filter that bypasses
        // DaoAuthenticationProvider, so UserDetails.isEnabled() is never
        // consulted. We must reject inactive users here, otherwise a
        // suspended/disabled user keeps its role and full access.
        if (!UserPrincipal.isActiveAndEnabled(user)) {
            log.warn("Blocked inactive user: id={} (role={}, active={})",
                    user.getId(), user.getRole(), user.isActive());
            writeForbidden(response, user);
            return;
        }

        // Admins that have not been approved by the super admin are locked
        // down: they may log in (so the app can tell them approval is
        // pending) but every admin endpoint stays blocked until approval.
        if (!UserPrincipal.isApproved(user) && !isPendingApprovalAllowedPath(request)) {
            log.warn("Blocked unapproved ADMIN: id={} (role={}, approved={})",
                    user.getId(), user.getRole(), user.isAdminApproved());
            writePendingApproval(response, user);
            return;
        }

        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("User authenticated: id={}", user.getId());
    }

    /**
     * Endpoints a not-yet-approved admin may still reach. Login returns their
     * profile (carrying {@code adminApproved=false}) so the app can show the
     * pending-approval screen, and {@code /users/me} re-reads it.
     */
    private boolean isPendingApprovalAllowedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/phone/login")
                || path.equals("/api/v1/users/me") || path.equals("/api/v1/auth/password/status");
    }

    private void writeForbidden(HttpServletResponse response, User user)
            throws IOException {
        String message = "Account is suspended";
        String userMessage = "Your account has been suspended. Contact the platform owner for details.";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(new ObjectMapper().writeValueAsString(
                Map.of(
                        "message", message,
                        "userMessage", userMessage,
                        "code", "account_suspended",
                        "status", HttpServletResponse.SC_FORBIDDEN
                )));
    }

    private void writePendingApproval(HttpServletResponse response, User user)
            throws IOException {
        String message = "Account pending approval";
        String userMessage = "Your admin account is awaiting approval from the super admin. You cannot use admin features until your account is approved.";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(new ObjectMapper().writeValueAsString(
                Map.of(
                        "message", message,
                        "userMessage", userMessage,
                        "code", "admin_pending",
                        "status", HttpServletResponse.SC_FORBIDDEN
                )));
    }
}