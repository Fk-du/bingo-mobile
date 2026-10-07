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
        boolean autoMark,
        /**
         * Total the winners of this game share. Public — it is the advertised
         * payout. The pot it came out of, and the admin's cut of what is left,
         * are not sent to players.
         */
        java.math.BigDecimal prizeAmount,
        List<PlayerCardView> playerCards,
        /**
         * Cards held for this player to look at but not yet paid for. Always empty
         * once the game leaves registration, and never a registration: these have
         * no claim rights until the player registers them.
         */
        List<PreviewCardView> previewCards,
        boolean hasPlayerCard,
        boolean isWinner,
        /** The winner's own share of the prize; null for anyone who did not win. */
        BigDecimal rewardAmount,
        /** How many cards won this game, so a winner can see the prize was shared. */
        int winnerCount,
        /**
         * Every winning card of this game (all cards marked winner), so the
         * results board can list them and, on tap, show the card itself. Public —
         * the whole room sees who won.
         */
        List<WinnerCardView> winnerCards,
        /**
         * Every banned card of this game, for the results board. Public — the
         * whole room sees the cards that were voided.
         */
        List<BannedCardView> bannedCards,
        /**
         * Only while the game is paused on a claim: the moment the automatic
         * reviewer decides everyone. Players count down to it and can still
         * claim Bingo until then. Null at every other status.
         */
        LocalDateTime claimWindowEndsAt,
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

    /** A card the player is holding to review. It is not registered and cannot claim. */
    public record PreviewCardView(
            Long cardId,
            int[][] numbers
    ) {}

    /** A winning card of this game, for the results board. */
    public record WinnerCardView(
            Long cardId,
            int[][] numbers,
            /** The winner's share of the prize; equal for every winner of the round. */
            BigDecimal rewardAmount
    ) {}

    /** A card banned in this game, for the results board. */
    public record BannedCardView(
            Long cardId,
            int[][] numbers
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
