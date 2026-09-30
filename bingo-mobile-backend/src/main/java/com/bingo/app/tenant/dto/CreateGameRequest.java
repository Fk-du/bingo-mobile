package com.bingo.app.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGameRequest {
    @Positive
    private BigDecimal entryFee;
    @Min(value = 2, message = "At least 2 players required")
    @Max(value = 100, message = "Cannot exceed 100 players")
    private Integer maxPlayers;
    private String winningPattern;

    /** Required when winningPattern == "CUSTOM". */
    private String customPatternName;
    /** JSON array of [row,col] pairs, e.g. [[0,0],[0,4]]. Required when winningPattern == "CUSTOM". */
    private String customPatternCells;
    @Min(value = 2, message = "Call interval must be at least 2 seconds")
    @Max(value = 300, message = "Call interval cannot exceed 300 seconds")
    private Integer callInterval;

    // The prize is deliberately NOT set here. An admin picks it once they can see
    // how many players registered, via PATCH /games/{id}/settings.

    /** Optional — defaults to true. When false, players mark numbers manually. */
    private Boolean autoMark;
}