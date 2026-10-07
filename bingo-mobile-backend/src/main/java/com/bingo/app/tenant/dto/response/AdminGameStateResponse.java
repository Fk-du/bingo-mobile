package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record AdminGameStateResponse(
        Long gameId,
        GameStatus status,
        BigDecimal entryFee,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        BigDecimal prizePool,
        BigDecimal prizeAmount,
        /** Lowest prize this admin may set for the current pot. */
        BigDecimal minPrize,
        /** Highest prize this admin may set for the current pot. */
        BigDecimal maxPrize,
        String winningPattern,
        Integer callInterval,
        boolean autoMark,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        List<Integer> calledNumbers,
        List<String> calledNumbersLabeled,
        int playerCount,
        /** Every winning card of this game, for the results board. */
        List<GameStateResponse.WinnerCardView> winnerCards,
        /** Every banned card of this game, for the results board. */
        List<GameStateResponse.BannedCardView> bannedCards,
        /**
         * Only while the game is paused on a claim: the moment the automatic
         * reviewer decides everyone. Players count down to it and can still
         * claim Bingo until then. Null at every other status.
         */
        LocalDateTime claimWindowEndsAt
) {}
