package com.bingo.app.tenant.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record GameSettingsUpdateRequest(
        @Min(value = 2, message = "At least 2 players required")
        @Max(value = 100, message = "Cannot exceed 100 players")
        Integer maxPlayers,
        @Min(value = 2, message = "Call interval must be at least 2 seconds")
        @Max(value = 300, message = "Call interval cannot exceed 300 seconds")
        Integer callInterval,
        String winningPattern,
        String customPatternName,
        String customPatternCells,
        /**
         * Total the winners share, chosen by the admin while registration is open.
         * Must sit inside the platform's min/maxPrizePercent of the pot collected
         * so far; a null value leaves the current prize untouched.
         */
        @Positive(message = "Prize must be greater than 0")
        BigDecimal prizeAmount,
        Boolean autoMark
) {
}
