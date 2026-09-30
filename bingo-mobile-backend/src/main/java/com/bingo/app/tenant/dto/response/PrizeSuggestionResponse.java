package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * What the admin needs to choose a prize for one of their games: how much has
 * been collected so far, the range the platform allows, and a suggestion based
 * on the rake they prefer in their automation settings.
 */
@Builder
public record PrizeSuggestionResponse(
        BigDecimal collected,
        BigDecimal minPrize,
        BigDecimal maxPrize,
        BigDecimal suggestedPrize,
        /** What the admin would keep at {@code suggestedPrize}. */
        BigDecimal suggestedCommission,
        BigDecimal currentPrize
) {
}
