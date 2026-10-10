package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.dto.DepositAccount;
import com.bingo.app.master.dto.DepositAccountsCodec;
import com.bingo.app.master.dto.request.UpdateProfileRequest;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_DEPOSIT_ACCOUNTS = 20;

    private final UserProfileService userProfileService;
    private final UserRepository userRepository;
    private final DepositAccountsCodec depositAccountsCodec;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userProfileService.buildProfile(principal.getUser()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER')")
    public ApiResponse<UserProfileResponse> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateProfileRequest body) {
        User user = principal.getUser();
        if (body.preferredLanguage() != null) {
            user.setPreferredLanguage(body.preferredLanguage());
        }
        if (body.businessName() != null) {
            user.setBusinessName(body.businessName());
        }
        if (body.depositAccounts() != null) {
            user.setDepositAccountInfo(depositAccountsCodec.write(sanitizeDepositAccounts(body.depositAccounts())));
        }
        userRepository.save(user);
        return ApiResponse.ok("Profile updated", userProfileService.buildProfile(user));
    }

    /**
     * Keeps only rows that carry an account number, trims the rest, and refuses to
     * store more than {@link #MAX_DEPOSIT_ACCOUNTS} so one malformed request can't
     * fill the column with noise.
     */
    private List<DepositAccount> sanitizeDepositAccounts(List<DepositAccount> accounts) {
        if (accounts.size() > MAX_DEPOSIT_ACCOUNTS) {
            throw new IllegalArgumentException(
                    "You can add at most " + MAX_DEPOSIT_ACCOUNTS + " deposit accounts.");
        }
        List<DepositAccount> valid = new ArrayList<>(accounts.size());
        for (DepositAccount account : accounts) {
            DepositAccount clean = DepositAccount.sanitize(account);
            if (clean != null && clean.hasNumber()) {
                valid.add(clean);
            }
        }
        return valid;
    }
}