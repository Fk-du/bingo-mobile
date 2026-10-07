package com.bingo.app.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-admin (per-tenant) template that drive automatic game creation and starting.
 * When {@code enabled} is true the system creates a new game from this template
 * after a finished game and opens registration; the admin starts it when ready —
 * with all the
 * same rules a manually created game follows (min 2 players, fair-play commit,
 * entry fees, commissions, claims, etc). Just automation, no rule changes.
 */
@Entity
@Table(name = "automation_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationConfig {

    /**
     * Claim window: how long after the first (real) Bingo claim a game stays in
     * CLAIM_PENDING so players who were a beat slow can still claim before the
     * engine reviews everyone. Every screen shows this countdown as the players'
     * window to press BINGO.
     */
    public static final int DEFAULT_REVIEW_GRACE_SECONDS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", unique = true)
    private Long adminUserId;

    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "entry_fee")
    private BigDecimal entryFee;
    @Column(name = "call_interval")
    private Integer callInterval;
    @Column(name = "rake_percent")
    private BigDecimal rakePercent;

    @Column(name = "winning_pattern")
    private String winningPattern;

    @Builder.Default
    @Column(name = "auto_mark")
    private Boolean autoMark = true;

    /** How long a game stays open for registrations before the system auto-starts it. */
    @Column(name = "registration_window_seconds")
    private Integer registrationWindowSeconds;

    /** Gap between a finished game and the next automatically created one. */
    @Column(name = "cooldown_seconds")
    private Integer cooldownSeconds;

    /**
     * When true, the system validates Bingo claims itself for every game of this
     * tenant, whether or not game automation is enabled: a provably complete
     * pattern is approved, a provably incomplete one is rejected and the card
     * banned, and anything the system cannot prove stays with the admin.
     * Defaults to on (a null column counts as true).
     */
    @Builder.Default
    @Column(name = "auto_approve")
    private Boolean autoApprove = true;

    /**
     * Legacy reject-only mode: with {@link #enabled} and autoApprove off, claims
     * are only rejected (cards missing the last called number) without ever
     * approving one.
     */
    @Builder.Default
    @Column(name = "auto_review")
    private Boolean autoReview = false;

    /** How long the auto-reviewer waits after the first claim before deciding (simultaneous winners). */
    @Builder.Default
    @Column(name = "review_grace_seconds")
    private Integer reviewGraceSeconds = DEFAULT_REVIEW_GRACE_SECONDS;

    /** Earliest moment the next game may be created (cooldown gate). */
    @Column(name = "next_game_at")
    private LocalDateTime nextGameAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}