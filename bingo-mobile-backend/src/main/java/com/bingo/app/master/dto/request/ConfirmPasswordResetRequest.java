package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPasswordResetRequest(
        @NotBlank String phone,
        @NotBlank String code,
        @NotBlank String newPassword
) {}