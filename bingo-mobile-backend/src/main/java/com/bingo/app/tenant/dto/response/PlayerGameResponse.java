package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A game as a PLAYER sees it.
 *
 * <p>Deliberately narrower than {@link AdminGameResponse}: the pot, the admin's
 * commission and how many other players registered are the house's business. The
 * prize IS shown — it is what the player is playing for, and a fixed published
 * prize reveals nothing about the rake.
 */
@Builder(toBuilder = true)
public record PlayerGameResponse(
        Long id,
        Long adminUserId,
        GameStatus status,
        BigDecimal entryFee,
        Integer maxPlayers,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        /** Total the winners share. Public, because it is the advertised payout. */
        BigDecimal prizeAmount,
        String winningPattern,
        String customPatternName,
        String customPatternCells,
        Integer callInterval,
        boolean autoMark,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        Boolean registered,
        Long activeGameId
) {
}
