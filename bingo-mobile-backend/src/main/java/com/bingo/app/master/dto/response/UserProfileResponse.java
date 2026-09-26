package com.bingo.app.master.dto.response;

import com.bingo.app.master.entity.User;
import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record UserProfileResponse(
        Long id,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String role,
        boolean verified,
        Long adminUserId,
        String businessName,
        String depositAccountInfo,
        boolean adminApproved,
        Long parentId,
        BigDecimal balance,
        BigDecimal frozenBalance,
        boolean active,
        String preferredLanguage
) {
    public static boolean isVerified(User user) {
        return user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank();
    }

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .telegramId(user.getTelegramId())
                .username(user.getTelegramUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .verified(isVerified(user))
                .adminUserId(user.getAdminUserId())
                .businessName(user.getBusinessName())
                .depositAccountInfo(user.getDepositAccountInfo())
                .adminApproved(user.isAdminApproved())
                .parentId(user.getParentId())
                .balance(user.getBalance())
                .frozenBalance(user.getFrozenBalance())
                .active(user.isActive())
                .preferredLanguage(user.getPreferredLanguage())
                .build();
    }
}