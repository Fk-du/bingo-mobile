package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PlayerCardHistoryResponse(
        GameResponse game,
        List<Card> cards,
        BigDecimal bet,
        BigDecimal win,
        BigDecimal refund,
        BigDecimal net
) {
    @Builder
    public record Card(
            Long cardId,
            boolean winner,
            boolean banned,
            LocalDateTime registeredAt,
            String claimResult,
            LocalDateTime claimedAt,
            LocalDateTime validatedAt,
            String rejectionReason
    ) {}
}