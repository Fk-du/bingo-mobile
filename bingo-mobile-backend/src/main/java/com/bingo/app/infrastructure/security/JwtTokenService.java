package com.bingo.app.infrastructure.security;

import com.bingo.app.master.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and parses HS256 JWTs for the mobile-app (phone+password) auth path.
 * The token subject is the master {@code users.id}; role and tenant are stored
 * as claims only as a convenience hint — the filter always reloads the master
 * User so changes (role, suspension, approval) take effect immediately.
 */
@Service
@Slf4j
public class JwtTokenService {

    private static final String FALLBACK_SECRET =
            "bingo-mobile-jwt-dev-secret-change-me-in-production-0001";

    private final SecretKey signingKey;
    private final long expiresMillis;

    public JwtTokenService(@Value("${app.security.jwt.secret:}") String secret,
                           @Value("${app.security.jwt.expires-minutes:10080}") long expiresMinutes) {
        // Dev-friendly fallback so the backend boots (and mobile auth works
        // locally) without configuring app.security.jwt.secret. Production must
        // set JWT_SECRET to a long random value.
        String resolved = (secret == null || secret.isBlank()) ? FALLBACK_SECRET : secret;
        if (resolved.length() < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 characters");
        }
        this.signingKey = Keys.hmacShaKeyFor(resolved.getBytes(StandardCharsets.UTF_8));
        this.expiresMillis = expiresMinutes * 60_000L;
    }

    public String issue(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresMillis))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @return the master users.id encoded in the subject, or null when the token
     *         is invalid, expired, or otherwise unparseable.
     */
    public Long parseSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }
}