package com.bingo.app.master.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Per-admin owner fee status: what accrued from their games, what they paid in cash, what is still owed. */
@Builder
public record AdminOwnerFeeSummaryResponse(
        Long adminUserId,
        String businessName,
        String username,
        BigDecimal accrued,
        BigDecimal settled,
        BigDecimal owed,
        LocalDateTime lastSettledAt
) {}