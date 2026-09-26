package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record AutomationConfigResponse(
        Long adminUserId,
        boolean enabled,
        BigDecimal entryFee,
        Integer maxPlayers,
        Integer callInterval,
        BigDecimal commissionPercent,
        String winningPattern,
        String customPatternName,
        String customPatternCells,
        boolean autoMark,
        Integer registrationWindowSeconds,
        Integer cooldownSeconds,
        boolean startWhenFull,
        boolean autoReview,
        Integer reviewGraceSeconds,
        LocalDateTime nextGameAt,
        LocalDateTime updatedAt
) {}