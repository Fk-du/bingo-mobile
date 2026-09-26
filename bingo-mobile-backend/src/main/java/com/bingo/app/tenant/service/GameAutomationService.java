package com.bingo.app.tenant.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.master.entity.TenantRegistry;
import com.bingo.app.master.repository.TenantRegistryRepository;
import com.bingo.app.tenant.dto.AutomationConfigRequest;
import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.tenant.dto.response.AutomationConfigResponse;
import com.bingo.app.tenant.entity.AutomationConfig;
import com.bingo.app.tenant.entity.BingoClaim;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.entity.GameCard;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.repository.AutomationConfigRepository;
import com.bingo.app.tenant.repository.BingoClaimRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Automatic game mode. Once an admin enables automation with a saved template,
 * this service keeps the loop running: it creates a new game (registration open)
 * after the previous one ends (respecting a cooldown), then starts it
 * automatically once the registration window elapses (or the table fills up),
 * provided the usual rules hold (2+ players, game owned by the admin, etc.).
 * Everything downstream — entry fees, prize pool, fair-play commit, calling,
 * claims, commissions — is exactly the same as a manually run game.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GameAutomationService {

    private final AutomationConfigRepository automationConfigRepository;
    private final GameRepository gameRepository;
    private final GameCardRepository gameCardRepository;
    private final BingoClaimRepository bingoClaimRepository;
    private final GameService gameService;
    private final GameEngineService gameEngineService;
    private final TenantRegistryRepository tenantRegistryRepository;

    @Qualifier("tenantTransactionTemplate")
    private final TransactionTemplate transactionTemplate;

    @Value("${bingo.automation.stale-registration-minutes:30}")
    private int staleRegistrationMinutes;

    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int DEFAULT_REGISTRATION_WINDOW_SECONDS = 180;
    private static final Set<String> SUPPORTED_PATTERNS = Set.of(
            "SINGLE_LINE", "DOUBLE_LINE", "TRIPLE_LINE", "FULL_HOUSE", "BLACKOUT", "FOUR_CORNERS",
            "X_SHAPE", "L_SHAPE", "T_SHAPE", "POSTAGE_STAMP",
            "PLUS", "FRAME", "DIAMOND", "Z_SHAPE");

    public AutomationConfigResponse getConfig(Long adminUserId) {
        return automationConfigRepository.findByAdminUserId(adminUserId)
                .map(this::toDto)
                .orElseGet(() -> defaultResponse(adminUserId));
    }

    public AutomationConfigResponse saveConfig(Long adminUserId, AutomationConfigRequest request) {
        AutomationConfig config = automationConfigRepository.findByAdminUserId(adminUserId)
                .orElseGet(() -> AutomationConfig.builder()
                        .adminUserId(adminUserId)
                        .build());

        config.setEntryFee(request.getEntryFee() != null ? request.getEntryFee() : BigDecimal.TEN);
        config.setMaxPlayers(request.getMaxPlayers() != null ? request.getMaxPlayers() : 50);
        config.setCallInterval(request.getCallInterval() != null ? request.getCallInterval() : 5);
        config.setCommissionPercent(request.getCommissionPercent() != null
                ? request.getCommissionPercent() : new BigDecimal("10.00"));
        config.setAutoMark(request.getAutoMark() == null || request.getAutoMark());
        config.setRegistrationWindowSeconds(request.getRegistrationWindowSeconds() != null
                ? request.getRegistrationWindowSeconds() : DEFAULT_REGISTRATION_WINDOW_SECONDS);
        config.setCooldownSeconds(request.getCooldownSeconds() != null
                ? request.getCooldownSeconds() : 15);
        config.setStartWhenFull(request.getStartWhenFull() == null || request.getStartWhenFull());
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setAutoReview(Boolean.TRUE.equals(request.getAutoReview()));
        config.setReviewGraceSeconds(request.getReviewGraceSeconds() != null
                ? request.getReviewGraceSeconds() : 2);

        String pattern = normalizePattern(request);
        config.setWinningPattern(pattern);
        if ("CUSTOM".equals(pattern)) {
            config.setCustomPatternName(request.getCustomPatternName());
            config.setCustomPatternCells(request.getCustomPatternCells());
        } else {
            config.setCustomPatternName(null);
            config.setCustomPatternCells(null);
        }

        // Enabling (or saving while enabled) schedules the next game immediately so the
        // scanner creates one on its very next tick instead of waiting on a stale cooldown.
        boolean wasEnabled = Boolean.TRUE.equals(config.getEnabled());
        if (wasEnabled && (config.getNextGameAt() == null || config.getNextGameAt().isBefore(LocalDateTime.now()))) {
            config.setNextGameAt(LocalDateTime.now());
        }

        config.setUpdatedAt(LocalDateTime.now());
        return toDto(automationConfigRepository.save(config));
    }

    public AutomationConfigResponse setEnabled(Long adminUserId, boolean enabled) {
        AutomationConfig config = automationConfigRepository.findByAdminUserId(adminUserId)
                .orElseThrow(() -> new GameProgressException("No automation config",
                        "Save an automation template first."));
        config.setEnabled(enabled);
        if (enabled) {
            config.setNextGameAt(LocalDateTime.now());
        }
        config.setUpdatedAt(LocalDateTime.now());
        return toDto(automationConfigRepository.save(config));
    }

    /**
     * Scans every tenant for automation work: create a game, start a ready one,
     * clean up stale registration-only tables. Runs sequentially, once every 10s.
     */
    @Scheduled(fixedDelay = 10_000)
    public void runAutomation() {
        List<TenantRegistry> tenants;
        try {
            tenants = tenantRegistryRepository.findAll();
        } catch (Exception e) {
            log.warn("Could not load tenant registry for automation: {}", e.getMessage());
            return;
        }

        for (TenantRegistry tenant : tenants) {
            Long adminUserId = tenant.getAdminUserId();
            String tenantId = TenantContext.tenantKeyForAdmin(adminUserId);
            try {
                TenantContext.setTenant(tenantId);
                transactionTemplate.executeWithoutResult(status -> processTenant(adminUserId));
            } catch (Exception e) {
                log.error("Automation failed for tenant {}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Rapid claim-review scanner. Runs on its own fast cycle (2s default) so an
     * auto-approved/rejected claim is resolved a few seconds after the grace window
     * elapses instead of waiting for the 10s game-lifecycle tick. The claim-timeout
     * backstop (reject-without-ban) is handled separately in GameEngineService.
     */
    @Scheduled(fixedDelayString = "${bingo.automation.claim-review-interval-ms:2000}")
    public void runClaimReview() {
        List<TenantRegistry> tenants;
        try {
            tenants = tenantRegistryRepository.findAll();
        } catch (Exception e) {
            log.warn("Could not load tenant registry for claim review: {}", e.getMessage());
            return;
        }

        for (TenantRegistry tenant : tenants) {
            Long adminUserId = tenant.getAdminUserId();
            String tenantId = TenantContext.tenantKeyForAdmin(adminUserId);
            try {
                TenantContext.setTenant(tenantId);
                transactionTemplate.executeWithoutResult(status -> reviewPendingClaimsForTenant(adminUserId));
            } catch (Exception e) {
                log.error("Claim review failed for tenant {}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void reviewPendingClaimsForTenant(Long adminUserId) {
        // Read-only hot path: claim review never mutates the config row, so a plain
        // read keeps the 2s scanner from contending with saveConfig/setEnabled or
        // the lifecycle loop's write-lock on the same row.
        AutomationConfig config = automationConfigRepository.findByAdminUserId(adminUserId).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())
                || !Boolean.TRUE.equals(config.getAutoReview())) {
            return;
        }

        Game claimPending = gameRepository
                .findByAdminUserIdAndStatus(adminUserId, GameStatus.CLAIM_PENDING)
                .orElse(null);
        if (claimPending == null) {
            return;
        }
        handleClaimReview(adminUserId, config, claimPending);
    }

    private void processTenant(Long adminUserId) {
        AutomationConfig config = automationConfigRepository.findByAdminUserIdForUpdate(adminUserId).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }

        List<Game> live = gameRepository.findAllByAdminUserIdAndStatusIn(adminUserId,
                List.of(GameStatus.REGISTRATION_OPEN, GameStatus.STARTING, GameStatus.IN_PROGRESS,
                        GameStatus.PAUSED, GameStatus.CLAIM_PENDING));

        Game openRegistration = live.stream()
                .filter(g -> g.getStatus() == GameStatus.REGISTRATION_OPEN)
                .findFirst()
                .orElse(null);

        if (openRegistration != null) {
            handleOpenRegistration(adminUserId, config, openRegistration);
            return;
        }

        // A game is running (starting / in progress / paused / claims pending) — wait.
        if (!live.isEmpty()) {
            return;
        }

        LocalDateTime nextGameAt = config.getNextGameAt();
        if (nextGameAt != null && nextGameAt.isAfter(LocalDateTime.now())) {
            return;
        }

        createAutomatedGame(adminUserId, config);
    }

    /**
     * Reject-only claim sanity check once the review grace period after the
     * first claim has elapsed (so simultaneous winners can still claim). The
     * system never approves a claim — it only auto-rejects a claim whose card
     * does not contain the last called number; everything else stays pending
     * for the admin to decide. When auto-review is disabled no check runs and
     * the game stays paused for the admin.
     */
    private void handleClaimReview(Long adminUserId, AutomationConfig config, Game game) {
        if (!Boolean.TRUE.equals(config.getAutoReview())) {
            return;
        }

        List<BingoClaim> pending = bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(game.getId(), "VALID");
        if (pending.isEmpty()) {
            return;
        }

        int grace = config.getReviewGraceSeconds() != null ? config.getReviewGraceSeconds() : 2;
        LocalDateTime oldestClaim = pending.stream()
                .map(BingoClaim::getClaimedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        if (oldestClaim != null && oldestClaim.plusSeconds(grace).isAfter(LocalDateTime.now())) {
            log.info("Automation: game {} claim {} in {}-second review grace; waiting for simultaneous claims",
                    game.getId(), grace);
            return;
        }

        log.info("Automation: checking pending claims of game {} for admin {}",
                game.getId(), adminUserId);
        gameEngineService.automatedClaimReview(game.getId(), adminUserId);
    }

    private void handleOpenRegistration(Long adminUserId, AutomationConfig config, Game game) {
        long playerCount = gameCardRepository.countDistinctPlayersByGameId(game.getId());

        // Clean up tables that never collected enough players so the loop keeps moving.
        if (playerCount < MIN_PLAYERS_TO_START && game.getCreatedAt() != null
                && game.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(staleRegistrationMinutes))) {
            try {
                gameService.cancelGame(game.getId(), adminUserId);
            } catch (GameProgressException e) {
                log.warn("Automation could not cancel stale game {}: {}", game.getId(), e.getMessage());
                return;
            }
            config.setNextGameAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            automationConfigRepository.save(config);
            log.info("Automation: cancelled stale registration game {} ({} player) for admin {}",
                    game.getId(), playerCount, adminUserId);
            return;
        }

        // The game NEVER starts on its own. Registration stays open until the admin
        // explicitly starts it (POST /games/{id}/start), which is the only thing that
        // moves the game to STARTING -> IN_PROGRESS and begins number calling.
        // Neither a full table nor an elapsed registration window starts it, and the
        // player count never closes registration on its own.
        log.info("Automation: game {} for admin {} holds registration open ({} players); waiting for admin to start it",
                game.getId(), adminUserId, playerCount);
    }

    private void createAutomatedGame(Long adminUserId, AutomationConfig config) {
        String pattern = randomPattern();
        CreateGameRequest request = CreateGameRequest.builder()
                .entryFee(config.getEntryFee() != null ? config.getEntryFee() : BigDecimal.TEN)
                .maxPlayers(config.getMaxPlayers() != null ? config.getMaxPlayers() : 50)
                .callInterval(config.getCallInterval() != null ? config.getCallInterval() : 5)
                .commissionPercent(config.getCommissionPercent() != null
                        ? config.getCommissionPercent() : new BigDecimal("10.00"))
                .autoMark(config.getAutoMark() == null || config.getAutoMark())
                .winningPattern(pattern)
                .build();

        gameService.createGameWithEntryFee(adminUserId, request);

        int cooldown = config.getCooldownSeconds() != null ? config.getCooldownSeconds() : 15;
        config.setNextGameAt(LocalDateTime.now().plusSeconds(cooldown));
        config.setUpdatedAt(LocalDateTime.now());
        automationConfigRepository.save(config);

        log.info("Automation: created automatic game for admin {} (entryFee {}, pattern {}, next cooldown {}s)",
                adminUserId, request.getEntryFee(), pattern, cooldown);
    }

    /**
     * Shuffle-and-pick the winning pattern for an automatic game. Chosen at random
     * from the supported set every round, so auto games vary their pattern; any
     * saved winningPattern/custom selection in the config is ignored here.
     */
    private String randomPattern() {
        List<String> patterns = new ArrayList<>(SUPPORTED_PATTERNS);
        return patterns.get(ThreadLocalRandom.current().nextInt(patterns.size()));
    }

    private String normalizePattern(AutomationConfigRequest request) {
        String pattern = request.getWinningPattern() != null ? request.getWinningPattern() : "SINGLE_LINE";
        if ("CUSTOM".equals(pattern)) {
            if (request.getCustomPatternName() == null || request.getCustomPatternName().trim().isEmpty()) {
                throw new GameProgressException("Custom pattern requires a name",
                        "Give your custom pattern a name.");
            }
            if (request.getCustomPatternCells() == null || request.getCustomPatternCells().trim().isEmpty()) {
                throw new GameProgressException("Custom pattern requires cells",
                        "Draw a pattern on the board first.");
            }
            return "CUSTOM";
        }
        if ("BLACKOUT".equals(pattern)) {
            return "FULL_HOUSE";
        }
        if (!SUPPORTED_PATTERNS.contains(pattern)) {
            throw new GameProgressException("Unsupported winning pattern: " + pattern,
                    "Unknown winning pattern. Pick one from the list.");
        }
        return pattern;
    }

    private AutomationConfigResponse toDto(AutomationConfig config) {
        return AutomationConfigResponse.builder()
                .adminUserId(config.getAdminUserId())
                .enabled(Boolean.TRUE.equals(config.getEnabled()))
                .entryFee(config.getEntryFee())
                .maxPlayers(config.getMaxPlayers())
                .callInterval(config.getCallInterval())
                .commissionPercent(config.getCommissionPercent())
                .winningPattern(config.getWinningPattern())
                .customPatternName(config.getCustomPatternName())
                .customPatternCells(config.getCustomPatternCells())
                .autoMark(config.getAutoMark() == null || config.getAutoMark())
                .registrationWindowSeconds(config.getRegistrationWindowSeconds())
                .cooldownSeconds(config.getCooldownSeconds())
                .startWhenFull(config.getStartWhenFull() == null || config.getStartWhenFull())
                .autoReview(Boolean.TRUE.equals(config.getAutoReview()))
                .reviewGraceSeconds(config.getReviewGraceSeconds() != null ? config.getReviewGraceSeconds() : 2)
                .nextGameAt(config.getNextGameAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private AutomationConfigResponse defaultResponse(Long adminUserId) {
        return AutomationConfigResponse.builder()
                .adminUserId(adminUserId)
                .enabled(false)
                .entryFee(BigDecimal.TEN)
                .maxPlayers(50)
                .callInterval(5)
                .commissionPercent(new BigDecimal("10.00"))
                .winningPattern("SINGLE_LINE")
                .autoMark(true)
                .registrationWindowSeconds(DEFAULT_REGISTRATION_WINDOW_SECONDS)
                .cooldownSeconds(15)
                .startWhenFull(true)
                .autoReview(false)
                .reviewGraceSeconds(2)
                .build();
    }
}