package com.bingo.app.master.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateCardRequest(
        @NotNull @Min(50) Integer quantity
) {}