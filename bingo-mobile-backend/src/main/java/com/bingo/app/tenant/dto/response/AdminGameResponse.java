package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A game as its ADMIN sees it: the full financial picture, including the pot
 * and how many players registered. Players get {@link PlayerGameResponse}
 * instead, which leaves those out.
 */
@Builder(toBuilder = true)
public record AdminGameResponse(
        Long id,
        Long adminUserId,
        GameStatus status,
        BigDecimal entryFee,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        BigDecimal prizePool,
        /** Total the winners share. Null until the admin sets it. */
        BigDecimal prizeAmount,
        String winningPattern,
        Integer callInterval,
        boolean autoMark,
        BigDecimal commissionEarned,
        /** Lowest prize this admin may set for the current pot. */
        BigDecimal minPrize,
        /** Highest prize this admin may set for the current pot. */
        BigDecimal maxPrize,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        Boolean registered,
        Long activeGameId,
        Integer registeredPlayers
) {}
