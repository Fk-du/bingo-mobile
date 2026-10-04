package com.bingo.app.tenant.controller;

import com.bingo.app.common.util.AdminIds;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.request.ClaimBingoRequest;
import com.bingo.app.tenant.dto.request.GameSettingsUpdateRequest;
import com.bingo.app.tenant.dto.response.AdminGameStateResponse;
import com.bingo.app.tenant.dto.response.BingoClaimResponse;
import com.bingo.app.tenant.dto.response.BingoClaimResultResponse;
import com.bingo.app.tenant.dto.response.AdminGameResponse;
import com.bingo.app.tenant.dto.response.CardRemovalResponse;
import com.bingo.app.tenant.dto.response.GameStateResponse;
import com.bingo.app.tenant.dto.response.PendingClaimCardResponse;
import com.bingo.app.tenant.dto.response.PlayerCardHistoryResponse;
import com.bingo.app.tenant.dto.response.PlayerGameResponse;
import com.bingo.app.tenant.dto.response.PreviewCardResponse;
import com.bingo.app.tenant.dto.response.RegisterResponse;
import com.bingo.app.master.enums.Role;
import com.bingo.app.tenant.service.CardService;
import com.bingo.app.tenant.service.GameEngineService;
import com.bingo.app.tenant.service.GameService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final GameEngineService gameEngineService;
    private final CardService cardService;
    private final TenantMapper tenantMapper;

    @GetMapping("/{id}/state")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    public ApiResponse<?> getGameState(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var role = principal.getUser().getRole();
        if (role == Role.ADMIN) {
            var state = gameEngineService.getAdminGameState(id);
            return ApiResponse.ok(tenantMapper.toAdminGameStateDto(state));
        }
        var state = gameEngineService.getGameState(id, principal.getUser().getId());
        return ApiResponse.ok(tenantMapper.toGameStateDto(state));
    }

    @GetMapping("/{id}/fairness")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    public ApiResponse<?> getFairnessProof(@PathVariable Long id) {
        return ApiResponse.ok(gameService.getFairnessProof(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminGameResponse> createGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGameRequest request) {
        var game = gameService.createGameWithEntryFee(principal.getUser().getId(), request);
        return ApiResponse.ok("Game created", game);
    }

    @PatchMapping("/{id}/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminGameResponse> updateGameSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody GameSettingsUpdateRequest request) {
        var game = gameService.updateGameSettings(id, principal.getUser().getId(),
                request.callInterval(), request.winningPattern(),
                request.prizeAmount(), request.autoMark());
        return ApiResponse.ok("Game settings updated", game);
    }

    @GetMapping("/{id}/prize-suggestion")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<com.bingo.app.tenant.dto.response.PrizeSuggestionResponse> getPrizeSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(gameService.getPrizeSuggestion(id, principal.getUser().getId()));
    }

    @PostMapping("/{id}/call-next")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> callNextNumber(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
        Integer number = gameEngineService.callNumber(id);
        if (number == null) {
            return ApiResponse.ok("No more numbers to call or game is not in progress");
        }
        return ApiResponse.ok("Called number: " + number);
    }

    @PostMapping("/{id}/call/{number}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> callSpecificNumber(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Integer number) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
        gameEngineService.callSpecificNumber(id, number);
        return ApiResponse.ok("Called number: " + number);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminGameResponse> startGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.startGameForAdmin(principal.getUser().getId(), id);
        gameEngineService.scheduleGameStart(game.id(), GameEngineService.COUNTDOWN_SECONDS,
                GameEngineService.REASON_START);
        return ApiResponse.ok("Game starting", game);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> cancelGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        gameService.cancelGame(id, principal.getUser().getId());
        return ApiResponse.ok("Game cancelled");
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> pauseGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
        gameEngineService.pauseGame(id);
        return ApiResponse.ok("Game paused");
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> resumeGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
        gameEngineService.resumeGame(id);
        return ApiResponse.ok("Game resumed");
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<String> endGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        gameEngineService.endGameWithoutWinner(id, "Manually ended by " + principal.getUser().getRole());
        return ApiResponse.ok("Game ended");
    }

    @GetMapping("/active")
    public ApiResponse<?> activeGames(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        switch (user.getRole()) {
            case ADMIN -> {
                return ApiResponse.ok(gameService.findOpenGamesForAdmin(user.getId()));
            }
            case PLAYER -> {
                Long adminUserId = AdminIds.adminUserId(user);
                if (adminUserId == null) return ApiResponse.ok(List.of());
                return ApiResponse.ok(gameService.findOpenGamesForPlayer(adminUserId, user.getId()));
            }
            default -> {
                return ApiResponse.ok(List.of());
            }
        }
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<RegisterResponse> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) com.bingo.app.tenant.dto.request.RegisterRequest request) {
        Integer count = request == null ? null : request.count();
        var gameCards = cardService.assignCardsAuto(id, principal.getUser().getId(), count == null ? 1 : count);
        return ApiResponse.ok("Registered for game", RegisterResponse.builder()
                .gameId(id)
                .cardIds(gameCards.stream().map(gc -> gc.card().id()).toList())
                .build());
    }

    /**
     * Show the player {@code count} cards without charging for any of them. The
     * cards are held for them, so nobody else is dealt the same numbers, and each
     * one is registered individually afterwards.
     */
    @PostMapping("/{id}/cards/preview")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<List<PreviewCardResponse>> previewCards(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) com.bingo.app.tenant.dto.request.RegisterRequest request) {
        Integer count = request == null ? null : request.count();
        var previews = cardService.previewCards(id, principal.getUser().getId(), count == null ? 1 : count);
        return ApiResponse.ok("Cards ready to review", previews);
    }

    /**
     * Reuse the player's most recent cards from previous games as fresh previews
     * for the target game (new game, clean slate - no marks carried over).
     */
    @PostMapping("/{id}/cards/preview-previous")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<List<PreviewCardResponse>> previewPreviousCards(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var previews = cardService.previewPreviousCards(id, principal.getUser().getId());
        return ApiResponse.ok("Previous cards ready to review", previews);
    }

    /** Pay the entry fee for one previewed card and deal it to the player. */
    @PostMapping("/{id}/cards/{cardId}/register")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<RegisterResponse> registerPreviewedCard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long cardId) {
        var gameCard = cardService.registerPreviewedCard(id, principal.getUser().getId(), cardId);
        return ApiResponse.ok("Card registered", RegisterResponse.builder()
                .gameId(id)
                .cardIds(java.util.List.of(gameCard.card().id()))
                .build());
    }

    /**
     * Take a card off the player's board. A card that was only being previewed
     * just goes away; a registered card is unregistered and its entry fee is
     * refunded to the balance and taken back out of the pot.
     */
    @DeleteMapping("/{id}/cards/{cardId}")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<CardRemovalResponse> unregisterCard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long cardId) {
        var result = cardService.unregisterCard(id, principal.getUser().getId(), cardId);
        String message = result.wasRegistered()
                ? "Card removed and entry fee refunded"
                : "Card removed";
        return ApiResponse.ok(message, new CardRemovalResponse(result.wasRegistered(), result.refund()));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<BingoClaimResultResponse> claim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) ClaimBingoRequest request) throws JsonProcessingException {
        var result = gameEngineService.claimBingo(id, principal.getUser().getId(),
                request == null ? null : request.getCardId(),
                request == null ? null : request.getMarkedNumbers(),
                request == null ? null : request.getAutoMark());
        String message;
        if (result.isBanned()) {
            message = "Invalid Bingo claim — you have been banned from this game";
        } else if (result.isPendingReview()) {
            message = "Bingo claimed! Waiting for admin review.";
        } else {
            message = "Bingo claim processed";
        }
        return ApiResponse.ok(message, BingoClaimResultResponse.builder()
                .valid(result.isValid())
                .claimId(result.getClaimId())
                .pendingReview(result.isPendingReview())
                .rewardAmount(result.getRewardAmount())
                .banned(result.isBanned())
                .build());
    }

    @GetMapping("/{id}/claims/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<BingoClaimResponse>> getPendingClaims(@PathVariable Long id) {
        return ApiResponse.ok(gameEngineService.getPendingClaims(id));
    }

    @GetMapping("/{id}/claims/cards")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    public ApiResponse<List<PendingClaimCardResponse>> getPendingClaimCards(@PathVariable Long id) {
        return ApiResponse.ok(gameEngineService.getPendingClaimCards(id));
    }

    @PostMapping("/{id}/claims/{claimId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> rejectClaim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long claimId,
            @RequestParam(defaultValue = "Claim rejected by admin") String reason) {
        gameEngineService.rejectClaim(id, claimId, principal.getUser().getId(), reason);
        return ApiResponse.ok("Claim rejected, game resumed");
    }

    @PostMapping("/{id}/claims/{claimId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BingoClaimResultResponse> approveClaim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long claimId) {
        var result = gameEngineService.approveClaim(id, claimId, principal.getUser().getId());
        String message = result.isGameEnded()
                ? "Claim approved. All confirmed winners were paid an equal share of the pot. Game ended."
                : "Claim approved. The pot is shared equally between every confirmed winner once the remaining claims are reviewed.";
        return ApiResponse.ok(message, BingoClaimResultResponse.builder()
                .valid(result.isValid())
                .claimId(claimId)
                .pendingReview(result.isPendingReview())
                .gameEnded(result.isGameEnded())
                .approvedCount(result.getApprovedCount())
                .rewardAmount(result.getRewardAmount())
                .banned(result.isBanned())
                .restarted(result.isRestarted())
                .build());
    }

    @PostMapping("/{id}/marks")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<Void> saveMarks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody ClaimBingoRequest request) {
        gameEngineService.saveMarks(id, principal.getUser().getId(),
                request.getCardId(), request.getMarkedNumbers(), request.getAutoMark());
        return ApiResponse.ok("Marks saved", null);
    }

    @PostMapping("/{id}/claims/approve-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BingoClaimResultResponse> approveAllClaims(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var result = gameEngineService.approveAllClaims(id, principal.getUser().getId());
        return ApiResponse.ok("All pending claims approved — winners share the prize. Game ended.",
                BingoClaimResultResponse.builder()
                        .valid(true)
                        .pendingReview(false)
                        .gameEnded(true)
                        .approvedCount(result.getApprovedCount())
                        .rewardAmount(result.getRewardAmount())
                        .build());
    }

    @PostMapping("/{id}/restart")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminGameResponse> restartGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.restartGame(id, principal.getUser().getId());
        gameEngineService.scheduleGameStart(id, GameEngineService.COUNTDOWN_SECONDS,
                GameEngineService.REASON_RESTART);
        gameEngineService.publishGameRestartedEvent(id);
        return ApiResponse.ok("Game restarted with a fresh number sequence", game);
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminGameResponse> audit(@PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        return ApiResponse.ok(game);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AdminGameResponse>> gameHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gameService.getAllGamesForAdmin(principal.getUser().getId()));
    }

    @GetMapping("/player/history")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<List<PlayerGameResponse>> playerGameHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gameService.getGamesForPlayer(principal.getUser().getId()));
    }

    @GetMapping("/player/history/cards")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<List<PlayerCardHistoryResponse>> playerCardHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gameService.getPlayerCardHistory(principal.getUser().getId()));
    }
}
