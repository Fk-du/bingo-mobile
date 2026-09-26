package com.bingo.app.master.dto.mapper;

import com.bingo.app.master.dto.response.*;
import com.bingo.app.master.entity.*;
import org.springframework.stereotype.Component;

@Component
public class MasterMapper {

    public UserProfileResponse toUserProfile(User user) {
        if (user == null) return null;
        return UserProfileResponse.builder()
                .id(user.getId())
                .telegramId(user.getTelegramId())
                .username(user.getTelegramUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .verified(UserProfileResponse.isVerified(user))
                .adminUserId(user.getAdminUserId())
                .businessName(user.getBusinessName())
                .adminApproved(user.isAdminApproved())
                .parentId(user.getParentId())
                .balance(user.getBalance())
                .frozenBalance(user.getFrozenBalance())
                .active(user.isActive())
                .build();
    }

    public AdminListItem toAdminListItem(User user) {
        if (user == null) return null;
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

    public OwnerFeeSettlementResponse toDto(OwnerFeeSettlement settlement) {
        if (settlement == null) return null;
        return OwnerFeeSettlementResponse.builder()
                .id(settlement.getId())
                .adminUserId(settlement.getAdminUserId())
                .amount(settlement.getAmount())
                .screenshotUrl(settlement.getScreenshotUrl())
                .status(settlement.getStatus())
                .approvedBy(settlement.getApprovedBy())
                .approvedAt(settlement.getApprovedAt())
                .rejectionReason(settlement.getRejectionReason())
                .createdAt(settlement.getCreatedAt())
                .build();
    }

    public InviteCodeResponse toDto(InviteCode inviteCode) {
        if (inviteCode == null) return null;
        return InviteCodeResponse.builder()
                .id(inviteCode.getId())
                .code(inviteCode.getCode())
                .creatorId(inviteCode.getCreatorId())
                .role(inviteCode.getRole())
                .active(inviteCode.isActive())
                .createdAt(inviteCode.getCreatedAt())
                .build();
    }

    public TenantRegistryResponse toDto(TenantRegistry registry) {
        if (registry == null) return null;
        return TenantRegistryResponse.builder()
                .id(registry.getId())
                .adminUserId(registry.getAdminUserId())
                .databaseName(registry.getDatabaseName())
                .createdAt(registry.getCreatedAt())
                .build();
    }

    public CardRequestResponse toDto(CardRequest request) {
        if (request == null) return null;
        return CardRequestResponse.builder()
                .id(request.getId())
                .adminUserId(request.getAdminUserId())
                .quantity(request.getQuantity())
                .status(request.getStatus())
                .approvedBy(request.getApprovedBy())
                .approvedAt(request.getApprovedAt())
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
