package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOwnerFeeSettlementRequest(
        @NotNull @Positive BigDecimal amount,
        String screenshotUrl
) {}