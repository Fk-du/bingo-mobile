package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PhoneLoginRequest(
        @NotBlank String phone,
        @NotBlank String password
) {}