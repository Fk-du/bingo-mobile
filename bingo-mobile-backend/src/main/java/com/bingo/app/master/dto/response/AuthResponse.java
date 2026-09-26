package com.bingo.app.master.dto.response;

public record AuthResponse(
        String jwt,
        UserProfileResponse user
) {}