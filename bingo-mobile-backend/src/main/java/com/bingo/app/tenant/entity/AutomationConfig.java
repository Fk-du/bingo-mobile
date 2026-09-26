package com.bingo.app.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-admin (per-tenant) template that drive automatic game creation and starting.
 * When {@code enabled} is true the system creates a new game from this template
 * after a finished game, opens registration, then starts it automatically once the
 * configured registration window elapses (or the table fills up) — with all the
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", unique = true)
    private Long adminUserId;

    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "entry_fee")
    private BigDecimal entryFee;
    @Column(name = "max_players")
    private Integer maxPlayers;
    @Column(name = "call_interval")
    private Integer callInterval;
    @Column(name = "commission_percent")
    private BigDecimal commissionPercent;

    @Column(name = "winning_pattern")
    private String winningPattern;
    @Column(name = "custom_pattern_name")
    private String customPatternName;
    @Column(name = "custom_pattern_cells", columnDefinition = "TEXT")
    private String customPatternCells;

    @Builder.Default
    @Column(name = "auto_mark")
    private Boolean autoMark = true;

    /** How long a game stays open for registrations before the system auto-starts it. */
    @Column(name = "registration_window_seconds")
    private Integer registrationWindowSeconds;

    /** Gap between a finished game and the next automatically created one. */
    @Column(name = "cooldown_seconds")
    private Integer cooldownSeconds;

    /** When true, a game starts immediately once it reaches maxPlayers. */
    @Builder.Default
    @Column(name = "start_when_full")
    private Boolean startWhenFull = true;

    /**
     * When true (and enabled), Bingo claims are approved/rejected automatically
     * instead of waiting on the admin. Invalid claims ban the claimed card.
     */
    @Builder.Default
    @Column(name = "auto_review")
    private Boolean autoReview = false;

    /** How long the auto-reviewer waits after the first claim before deciding (simultaneous winners). */
    @Builder.Default
    @Column(name = "review_grace_seconds")
    private Integer reviewGraceSeconds = 2;

    /** Earliest moment the next game may be created (cooldown gate). */
    @Column(name = "next_game_at")
    private LocalDateTime nextGameAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}