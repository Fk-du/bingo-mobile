package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record AutomationConfigResponse(
        Long adminUserId,
        boolean enabled,
        BigDecimal entryFee,
        Integer callInterval,
        BigDecimal rakePercent,
        String winningPattern,
        boolean autoMark,
        Integer registrationWindowSeconds,
        Integer cooldownSeconds,
        boolean autoApprove,
        boolean autoReview,
        Integer reviewGraceSeconds,
        LocalDateTime nextGameAt,
        LocalDateTime updatedAt
) {}