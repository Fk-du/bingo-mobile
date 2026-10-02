package com.bingo.app.tenant.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Template used by the automatic game mode. When enabled, the system keeps
 * creating and starting games from this configuration. Mirrors the fields a
 * manual game would be created with, plus automation-only timing options.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationConfigRequest {
    @NotNull
    @Positive
    private BigDecimal entryFee;

    @NotNull
    @Min(value = 2, message = "Call interval must be at least 2 seconds")
    @Max(value = 300, message = "Call interval cannot exceed 300 seconds")
    private Integer callInterval;

    @NotNull
    @DecimalMin(value = "0", message = "Preferred rake must be at least 0%")
    @DecimalMax(value = "90", message = "Preferred rake cannot exceed 90%")
    private BigDecimal rakePercent;

    private String winningPattern;
    private Boolean autoMark;

    @NotNull
    @Min(value = 60, message = "Registration window must be at least 60 seconds (1 minute)")
    @Max(value = 3600, message = "Registration window cannot exceed 1 hour")
    private Integer registrationWindowSeconds;

    @NotNull
    @Min(value = 0, message = "Cooldown cannot be negative")
    @Max(value = 3600, message = "Cooldown cannot exceed 1 hour")
    private Integer cooldownSeconds;

    private Boolean enabled;

    /** When true (with automation enabled), claims are auto-approved/rejected without the admin. */
    private Boolean autoReview;

    /** Grace period (seconds) the reviewer waits for simultaneous claims before resolving. */
    @Min(value = 1, message = "Review grace must be at least 1 second")
    @Max(value = 60, message = "Review grace cannot exceed 60 seconds")
    private Integer reviewGraceSeconds;
}