package com.bingo.app.tenant.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.response.CardPoolResponse;
import com.bingo.app.tenant.dto.response.CardResponse;
import com.bingo.app.tenant.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    /**
     * Free cards in the caller's tenant, paged by card number (100 per page by default).
     * Page numbers are 1-based for callers.
     */
    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    public ApiResponse<CardPoolResponse> available(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        int zeroBasedPage = Math.max(0, page - 1);
        return ApiResponse.ok(CardPoolResponse.builder()
                .cards(cardService.getAvailableCards(zeroBasedPage, size))
                .total(cardService.countAvailableCards())
                .page(page)
                .size(size)
                .build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    public ApiResponse<CardResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(cardService.getCard(id));
    }
}