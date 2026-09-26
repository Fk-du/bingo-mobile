package com.bingo.app.master.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.CreateCardRequest;
import com.bingo.app.master.dto.response.CardRequestResponse;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.CardRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cards/requests")
@RequiredArgsConstructor
public class CardRequestController {

    private final CardRequestService cardRequestService;
    private final MasterMapper masterMapper;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CardRequestResponse> request(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCardRequest request) {
        var saved = cardRequestService.requestCardStock(principal.getUser().getId(), request.quantity());
        return ApiResponse.ok("Card request submitted", masterMapper.toDto(saved));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<CardRequestResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        return switch (user.getRole()) {
            case SUPER_ADMIN -> ApiResponse.ok(
                    cardRequestService.listPending().stream().map(masterMapper::toDto).toList());
            case ADMIN -> ApiResponse.ok(
                    cardRequestService.listForAgent(user.getId()).stream().map(masterMapper::toDto).toList());
            default -> ApiResponse.ok(List.of());
        };
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> handle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "").toUpperCase();
        switch (action) {
            case "APPROVE" -> cardRequestService.approveCardRequest(id, principal.getUser().getId());
            case "REJECT" -> cardRequestService.rejectCardRequest(id, principal.getUser().getId(), body.get("reason"));
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        return ApiResponse.ok("Card request " + action.toLowerCase() + "d");
    }
}