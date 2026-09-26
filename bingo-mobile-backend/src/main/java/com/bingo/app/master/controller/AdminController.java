package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.AdminStatusRequest;
import com.bingo.app.master.dto.request.AdminWarningRequest;
import com.bingo.app.master.dto.request.CreateOwnerFeeSettlementRequest;
import com.bingo.app.master.dto.response.AdminListItem;
import com.bingo.app.master.dto.response.AdminWarningResponse;
import com.bingo.app.master.dto.response.AdminOwnerFeeSummaryResponse;
import com.bingo.app.master.dto.response.AgentStatsResponse;
import com.bingo.app.master.dto.response.OwnerFeeSettlementResponse;
import com.bingo.app.master.dto.response.OwnerFeeSummaryResponse;
import com.bingo.app.master.entity.AdminWarning;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.tenant.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/admins", "/api/v1/agents"})
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final InviteService inviteService;
    private final WalletService walletService;
    private final MasterMapper masterMapper;

    @Value("${app.telegram.bot.username}")
    private String botUsername;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminListItem>> listAdmins() {
        return ApiResponse.ok(userService.findAllByRole(Role.ADMIN));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> inviteAdmin(@AuthenticationPrincipal UserPrincipal principal) {
        String link = inviteService.generateInviteLinkForUser(principal.getUser().getId(), botUsername);
        return ApiResponse.ok("Invite link generated", link);
    }

    @PatchMapping("/{adminUserId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminListItem> updateAdminStatus(
            @PathVariable Long adminUserId,
            @Valid @RequestBody AdminStatusRequest request) {
        var admin = switch (request.status().toUpperCase()) {
            case "APPROVE" -> userService.approveAdmin(adminUserId);
            case "REJECT" -> userService.rejectAdmin(adminUserId);
            case "SUSPEND" -> userService.suspendAdmin(adminUserId);
            case "RESUME" -> userService.resumeAdmin(adminUserId);
            default -> throw new IllegalArgumentException("Unknown status: " + request.status());
        };
        return ApiResponse.ok("Admin status updated", admin);
    }

    @PostMapping("/{adminUserId}/warn")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminWarningResponse> warnAdmin(
            @PathVariable Long adminUserId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminWarningRequest request) {
        AdminWarning warning = userService.warnAdmin(adminUserId, request.reason(), principal.getUser().getId());
        return ApiResponse.ok("Admin warned", toWarningResponse(warning));
    }

    @GetMapping("/{adminUserId}/warnings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminWarningResponse>> listWarnings(@PathVariable Long adminUserId) {
        return ApiResponse.ok(userService.getWarningsForAdmin(adminUserId).stream()
                .map(this::toWarningResponse).toList());
    }

    @GetMapping("/{adminUserId}/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AgentStatsResponse> agentStats(@PathVariable Long adminUserId) {
        return ApiResponse.ok(userService.getAgentStats(adminUserId));
    }

    private AdminWarningResponse toWarningResponse(AdminWarning w) {
        return new AdminWarningResponse(
                w.getId(), w.getAdminUserId(), w.getReason(),
                w.getCreatedBy(), w.getCreatedAt() != null ? w.getCreatedAt() : LocalDateTime.now());
    }

    // =========================================================
    // OWNER FEE SETTLEMENTS (cash paid to the owner, screenshot as proof)
    // =========================================================

    @GetMapping("/fee-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OwnerFeeSummaryResponse> ownerFeeSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(walletService.getOwnerFeeSummaryForAdmin(principal.getUser().getId()));
    }

    @GetMapping("/fee-summary/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminOwnerFeeSummaryResponse>> allOwnerFeeSummary() {
        return ApiResponse.ok(walletService.getOwnerFeeSummaryForAllAdmins());
    }

    @PostMapping("/fee-settlements")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OwnerFeeSettlementResponse> createFeeSettlement(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOwnerFeeSettlementRequest request) {
        var settlement = walletService.createOwnerFeeSettlement(
                principal.getUser().getId(), request.amount(), request.screenshotUrl());
        return ApiResponse.ok("Owner fee settlement submitted", masterMapper.toDto(settlement));
    }

    @GetMapping("/fee-settlements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<OwnerFeeSettlementResponse>> listFeeSettlements(
            @AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        return switch (user.getRole()) {
            case SUPER_ADMIN -> ApiResponse.ok(
                    walletService.getAllOwnerFeeSettlements().stream()
                            .map(masterMapper::toDto).toList());
            case ADMIN -> ApiResponse.ok(
                    walletService.getOwnerFeeSettlementsByAdmin(user.getId()).stream()
                            .map(masterMapper::toDto).toList());
            default -> ApiResponse.ok(List.of());
        };
    }

    @PatchMapping("/fee-settlements/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> handleFeeSettlement(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "").toUpperCase();
        switch (action) {
            case "APPROVE" -> walletService.approveOwnerFeeSettlement(id, principal.getUser().getId());
            case "REJECT" -> walletService.rejectOwnerFeeSettlement(id, principal.getUser().getId(), body.get("reason"));
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        return ApiResponse.ok("Owner fee settlement " + action.toLowerCase() + "d");
    }
}
