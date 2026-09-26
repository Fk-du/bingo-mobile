package com.bingo.app.tenant.dto.request;

/**
 * Body for game registration. {@code count} auto-picks that many free cards
 * at once (cheap for the client — no need to list the whole pool). When
 * omitted, the server registers a single card.
 */
public record RegisterRequest(
        Integer count
) {}