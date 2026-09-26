package com.bingo.app.master.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

/** Owner fee balance for an admin: what accrued from games, what was paid in cash, what is still owed. */
@Builder
public record OwnerFeeSummaryResponse(
        BigDecimal accrued,
        BigDecimal settled,
        BigDecimal owed
) {}