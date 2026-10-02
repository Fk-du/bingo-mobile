package com.bingo.app.tenant.dto.response;

import java.math.BigDecimal;

/**
 * Result of removing a card from the player's board. {@code wasRegistered} is
 * false for a card that was only being previewed, and {@code refund} is zero in
 * that case because nothing was ever paid for it.
 */
public record CardRemovalResponse(
        boolean wasRegistered,
        BigDecimal refund
) {}
