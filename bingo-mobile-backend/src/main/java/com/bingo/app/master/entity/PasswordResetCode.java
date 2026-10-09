package com.bingo.app.master.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One-time password-reset code issued to a master user. Only the SHA-256 hash
 * of the code is stored, so the six digits never sit in the database in clear
 * text. A code is single-use: it dies on a successful reset, on expiry, or
 * after {@code MAX_WRONG_ATTEMPTS} wrong entries.
 */
@Entity
@Table(name = "password_reset_codes", indexes = {
        @Index(name = "idx_password_reset_codes_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    /** Hex SHA-256 of the six-digit code. */
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}