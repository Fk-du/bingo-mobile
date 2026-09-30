package com.bingo.app.tenant.dto.response;

import com.bingo.app.tenant.enums.GameStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record GameStateResponse(
        Long gameId,
        GameStatus status,
        Integer currentCallIndex,
        Integer totalNumbersCalled,
        List<Integer> calledNumbers,
        List<String> calledNumbersLabeled,
        String winningPattern,
        String customPatternName,
        String customPatternCells,
        boolean autoMark,
        /**
         * Total the winners of this game share. Public — it is the advertised
         * payout. The pot it came out of, and the admin's cut of what is left,
         * are not sent to players.
         */
        java.math.BigDecimal prizeAmount,
        List<PlayerCardView> playerCards,
        boolean hasPlayerCard,
        boolean isWinner,
        /** The winner's own share of the prize; null for anyone who did not win. */
        BigDecimal rewardAmount,
        /** How many cards won this game, so a winner can see the prize was shared. */
        int winnerCount,
        String fairnessHash,
        LocalDateTime startTime
) {
    /**
     * One of the caller's cards in this game. {@code banned} cards can no longer
     * claim Bingo but stay on screen for watching.
     */
    public record PlayerCardView(
            Long cardId,
            int[][] numbers,
            boolean winner,
            boolean banned,
            List<Integer> markedNumbers,
            Boolean autoMark
    ) {}

    public static String numberToLabel(Integer number) {
        if (number == null || number < 1 || number > 75) return String.valueOf(number);
        return switch ((number - 1) / 15) {
            case 0 -> "B" + number;
            case 1 -> "I" + number;
            case 2 -> "N" + number;
            case 3 -> "G" + number;
            case 4 -> "O" + number;
            default -> String.valueOf(number);
        };
    }
}
