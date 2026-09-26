package com.bingo.app.master.dto.response;

import com.bingo.app.master.enums.FundStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CardRequestResponse(
        Long id,
        Long adminUserId,
        Integer quantity,
        FundStatus status,
        Long approvedBy,
        LocalDateTime approvedAt,
        String rejectionReason,
        LocalDateTime createdAt
) {}