package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CardResponse(
        Long id,
        String numbers,
        String numbersHash,
        boolean used,
        Integer usageCount,
        Integer gamesWon,
        Double winRate,
        LocalDateTime createdAt,
        int[][] grid
) {}
