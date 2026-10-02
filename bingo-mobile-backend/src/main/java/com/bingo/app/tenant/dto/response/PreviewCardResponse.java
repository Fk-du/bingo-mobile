package com.bingo.app.tenant.dto.response;

import lombok.Builder;

/**
 * A card held for the player to look at before paying for it. Registering it is
 * a separate step, so this is never returned as a registered card.
 */
@Builder
public record PreviewCardResponse(
        Long cardId,
        int[][] numbers
) {}
