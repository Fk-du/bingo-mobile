package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record RegisterResponse(
        Long gameId,
        Long cardId,
        List<Long> cardIds
) {}