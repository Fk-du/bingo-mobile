package com.bingo.app.tenant.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.AutomationConfigRequest;
import com.bingo.app.tenant.dto.response.AutomationConfigResponse;
import com.bingo.app.tenant.service.GameAutomationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/automation")
@RequiredArgsConstructor
public class GameAutomationController {

    private final GameAutomationService gameAutomationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AutomationConfigResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gameAutomationService.getConfig(principal.getUser().getId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AutomationConfigResponse> save(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AutomationConfigRequest request) {
        return ApiResponse.ok("Automation template saved",
                gameAutomationService.saveConfig(principal.getUser().getId(), request));
    }

    @PostMapping("/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AutomationConfigResponse> enable(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Automatic game mode enabled",
                gameAutomationService.setEnabled(principal.getUser().getId(), true));
    }

    @PostMapping("/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AutomationConfigResponse> disable(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Automatic game mode disabled",
                gameAutomationService.setEnabled(principal.getUser().getId(), false));
    }
}