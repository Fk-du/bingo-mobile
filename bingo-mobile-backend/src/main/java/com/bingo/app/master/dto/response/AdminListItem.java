package com.bingo.app.master.dto.response;

import com.bingo.app.master.entity.User;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminListItem(
        Long adminUserId,
        boolean approved,
        String businessName,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        BigDecimal balance,
        BigDecimal frozenBalance,
        boolean active
) {
    public static AdminListItem from(User user) {
        return AdminListItem.builder()
                .adminUserId(user.getId())
                .approved(user.isAdminApproved())
                .businessName(user.getBusinessName())
                .telegramId(user.getTelegramId())
                .username(user.getTelegramUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .balance(user.getBalance())
                .frozenBalance(user.getFrozenBalance())
                .active(user.isActive())
                .build();
    }
}
