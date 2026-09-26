package com.bingo.app.tenant.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record CardPoolResponse(
        List<CardResponse> cards,
        long total,
        int page,
        int size
) {}