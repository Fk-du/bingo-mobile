package com.bingo.app.tenant.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.master.entity.TenantRegistry;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.TenantRegistryRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.service.NotificationService;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.BingoClaimResponse;
import com.bingo.app.tenant.dto.response.GameStateResponse;
import com.bingo.app.tenant.entity.*;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.exception.RequestAlreadyProcessedException;
import com.bingo.app.tenant.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameEngineService {

    private final GameRepository gameRepository;
    private final CalledNumberRepository calledNumberRepository;
    private final GameCardRepository gameCardRepository;
    private final com.bingo.app.tenant.repository.CardPreviewRepository cardPreviewRepository;
    private final BingoClaimRepository bingoClaimRepository;
    private final WalletService walletService;
    private final CardService cardService;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Qualifier("tenantTransactionTemplate")
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final TenantMapper tenantMapper;
    private final TenantRegistryRepository tenantRegistryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final GameService gameService;
    private final PrizeRules prizeRules;

    // Not part of the positional constructor (tests build the service directly):
    // injected by Spring when present, so the state readers can report the
    // claim-window deadline without stretching every unit test's stubs. When
    // null the review grace falls back to the shared default.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AutomationConfigRepository automationConfigRepository;


    @Value("${bingo.game.claim-timeout-seconds:300}")
    private int claimTimeoutSeconds;

    // Track active game schedulers
    private final ScheduledThreadPoolExecutor taskScheduler = new ScheduledThreadPoolExecutor(4);
    private final Map<Long, ScheduledFuture<?>> activeGameTasks = new ConcurrentHashMap<>();
    private final Map<Long, String> gameTenantContexts = new ConcurrentHashMap<>();

    /**
     * Start automatic number calling for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void startCalling(Long gameId) {
        startCallingInternal(gameId);
    }

    /**
     * Announce the STARTING countdown and schedule the transition to IN_PROGRESS
     * (with number calling) once the countdown elapses.
     */
    public void scheduleGameStart(Long gameId, int countdownSeconds) {
        scheduleGameStart(gameId, countdownSeconds, REASON_START);
    }

    /**
     * @param reason why the countdown runs — {@code start}, {@code resume},
     *               {@code restart} or {@code claim_resolved} — so every player is
     *               told what is happening before the first number is called again.
     */
    public void scheduleGameStart(Long gameId, int countdownSeconds, String reason) {
        String tenantId = TenantContext.getTenant();
        Game game = gameRepository.findById(gameId).orElse(null);
        java.time.LocalDateTime startTime = game != null ? game.getStartTime() : java.time.LocalDateTime.now().plusSeconds(countdownSeconds);
        publishGameStatusEvent(gameId, GameStatus.STARTING, startTime, reason);
        taskScheduler.schedule(() -> {
            TenantContext.setTenant(tenantId);
            try {
                transactionTemplate.execute(status -> {
                    beginCallingAfterCountdown(gameId);
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to start game {} after countdown: {}", gameId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }, Math.max(0, countdownSeconds), java.util.concurrent.TimeUnit.SECONDS);
        log.info("Game {} starting in {} seconds ({})", gameId, countdownSeconds, reason);
    }

    /**
     * Put a stopped game back on its feet with a visible countdown. Every way a
     * game resumes — an admin resuming a pause, a rejected claim, a claim that
     * timed out — goes through here, so players always get the same warning
     * before the numbers start again instead of wondering why the game stalled.
     */
    public void resumeWithCountdown(Long gameId, String reason) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        resumeWithCountdown(gameId, game, reason);
    }

    private void resumeWithCountdown(Long gameId, Game game, String reason) {
        if (game.getStatus() != GameStatus.PAUSED && game.getStatus() != GameStatus.CLAIM_PENDING
                && game.getStatus() != GameStatus.STARTING) {
            throw new GameProgressException("Game is not in a resumable state",
                    "This game cannot be resumed right now.");
        }
        game.setStatus(GameStatus.STARTING);
        game.setStartTime(java.time.LocalDateTime.now().plusSeconds(COUNTDOWN_SECONDS));
        gameRepository.save(game);
        scheduleGameStart(gameId, COUNTDOWN_SECONDS, reason);
    }

    private void beginCallingAfterCountdown(Long gameId) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) return;
        if (game.getStatus() == GameStatus.STARTING) {
            game.setStatus(GameStatus.IN_PROGRESS);
            game.setStartTime(LocalDateTime.now());
            gameRepository.save(game);
        }
        if (game.getStatus() == GameStatus.IN_PROGRESS) {
            startCallingInternal(gameId);
        }
    }

    private void startCallingInternal(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        stopCalling(gameId);

        String tenantId = TenantContext.getTenant();
        gameTenantContexts.put(gameId, tenantId);

        int interval = game.getCallInterval() != null ? game.getCallInterval() : 5;

        // Initial delay is set to `interval` (not 0): the scheduleAtFixedRate task starts
        // on a separate thread, and without the delay the first tick can read the game's
        // status *before* the STARTING->IN_PROGRESS transition commit is visible, hitting
        // the status guard below and cancelling the scheduler forever.
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
            TenantContext.setTenant(tenantId);
            try {
                transactionTemplate.execute(status -> {
                    callNextNumber(gameId);
                    return null;
                });
            } catch (Exception e) {
                log.error("Error calling number for game {}: {}", gameId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }, interval, interval, java.util.concurrent.TimeUnit.SECONDS);

        activeGameTasks.put(gameId, future);

        publishGameStatusEvent(gameId, GameStatus.IN_PROGRESS);

        log.info("Started automatic number calling for game {} every {} seconds", gameId, interval);
    }

    /**
     * Recover active game schedulers after server restart.
     * Iterates over all registered tenants and restarts schedulers for IN_PROGRESS games.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverActiveGames() {
        log.info("Recovering active game schedulers...");

        List<TenantRegistry> tenants;
        try {
            tenants = tenantRegistryRepository.findAll();
        } catch (Exception e) {
            log.warn("Could not load tenant registry for recovery: {}", e.getMessage());
            return;
        }

        int recovered = 0;
        for (TenantRegistry tenant : tenants) {
            String tenantId = "agent_" + tenant.getAdminUserId();
            try {
                TenantContext.setTenant(tenantId);
                List<Game> activeGames = gameRepository.findByStatus(GameStatus.IN_PROGRESS);
                for (Game game : activeGames) {
                    try {
                        startCalling(game.getId());
                        recovered++;
                        log.info("Recovered scheduler for game {} (tenant {})", game.getId(), tenantId);
                    } catch (Exception e) {
                        log.error("Failed to recover scheduler for game {}: {}", game.getId(), e.getMessage());
                    }
                }
                // Resume games that were mid-countdown when the server restarted
                List<Game> startingGames = gameRepository.findByStatus(GameStatus.STARTING);
                for (Game game : startingGames) {
                    try {
                        long delayMs = game.getStartTime() == null ? 0
                                : java.time.Duration.between(LocalDateTime.now(), game.getStartTime()).toMillis();
                        if (delayMs <= 0) {
                            transactionTemplate.execute(status -> {
                                beginCallingAfterCountdown(game.getId());
                                return null;
                            });
                        } else {
                            scheduleGameStart(game.getId(), (int) Math.ceil(delayMs / 1000.0));
                        }
                        recovered++;
                        log.info("Resumed countdown/start for game {} (tenant {})", game.getId(), tenantId);
                    } catch (Exception e) {
                        log.error("Failed to resume starting game {}: {}", game.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to query games for tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }

        log.info("Active game recovery complete. Recovered {} game(s).", recovered);
    }

    /**
     * Auto-reject claims that have been pending longer than the configured timeout.
     * Runs every 30 seconds across all tenants.
     */
    @Scheduled(fixedDelay = 30_000)
    public void enforceClaimTimeout() {
        List<TenantRegistry> tenants;
        try {
            tenants = tenantRegistryRepository.findAll();
        } catch (Exception e) {
            return;
        }

        for (TenantRegistry tenant : tenants) {
            String tenantId = "agent_" + tenant.getAdminUserId();
            try {
                TenantContext.setTenant(tenantId);
                List<Game> pendingGames = gameRepository.findByStatus(GameStatus.CLAIM_PENDING);
                for (Game game : pendingGames) {
                    try {
                        processClaimTimeout(game);
                    } catch (Exception e) {
                        log.error("Error processing claim timeout for game {}: {}", game.getId(), e.getMessage());
                    }
                }
                // Claims on games that already ended before a slow/racing claim was
                // recorded can never be resolved by approve/reject — void them so
                // they don't sit half-resolved forever. Never a card ban.
                try {
                    resolveStrandedClaimsOnEndedGames();
                } catch (Exception e) {
                    log.error("Error resolving stranded claims for tenant {}: {}", tenantId, e.getMessage());
                }
            } catch (Exception e) {
                log.error("Error checking claim timeouts for tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Void VALID claims that were recorded on a game which has already ended
     * (for example a claim that raced a simultaneous-winner approval). No ban,
     * no resume — the game is over; this only keeps the claim ledger honest.
     */
    public void resolveStrandedClaimsOnEndedGames() {
        transactionTemplate.executeWithoutResult(status -> {
            List<BingoClaim> stranded = bingoClaimRepository.findUnresolvedClaimsOnEndedGames();
            for (BingoClaim claim : stranded) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                Long adminId = gameRepository.findById(claim.getGameId())
                        .map(Game::getAdminUserId)
                        .orElse(null);
                int claimed = bingoClaimRepository.claimForProcessing(claim.getId(), adminId, now);
                if (claimed == 0) {
                    continue;
                }
                claim.setValidatedBy(adminId);
                claim.setValidatedAt(now);
                claim.setResult("REJECTED");
                claim.setRejectionReason("Game already ended before this claim could be reviewed");
                bingoClaimRepository.save(claim);
                log.info("Game {}: stranded claim {} voided (game already ended).",
                        claim.getGameId(), claim.getId());
            }
        });
    }

    private void processClaimTimeout(Game game) {
        transactionTemplate.executeWithoutResult(status -> processClaimTimeoutLocked(game.getId()));
    }

    /**
     * Claim-timeout handling runs under the same game-row lock (and atomic
     * per-claim guard) as approve/reject/auto-review, so it can never interleave
     * a mid-flight decision. Claims are voided WITHOUT a card ban — the backstop
     * exists to unstick a game, not to penalise anyone.
     */
    private void processClaimTimeoutLocked(Long gameId) {
        Game locked = gameRepository.findByIdForUpdate(gameId).orElse(null);
        if (locked == null || locked.getStatus() != GameStatus.CLAIM_PENDING) {
            return;
        }

        List<BingoClaim> pendingClaims = bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");

        if (pendingClaims.isEmpty()) {
            return;
        }

        BingoClaim oldestClaim = pendingClaims.get(0);
        if (oldestClaim.getClaimedAt() == null) {
            return;
        }

        Duration elapsed = Duration.between(oldestClaim.getClaimedAt(), LocalDateTime.now());
        if (elapsed.getSeconds() < claimTimeoutSeconds) {
            return;
        }

        log.warn("Game {}: Claim timeout reached ({}s elapsed). Auto-rejecting {} pending claim(s).",
                locked.getId(), elapsed.getSeconds(), pendingClaims.size());

        for (BingoClaim claim : pendingClaims) {
            java.time.LocalDateTime rejectedAt = java.time.LocalDateTime.now();
            int claimed = bingoClaimRepository.claimForProcessing(claim.getId(), locked.getAdminUserId(), rejectedAt);
            if (claimed == 0) {
                continue;
            }
            claim.setValidatedBy(locked.getAdminUserId());
            claim.setValidatedAt(rejectedAt);
            claim.setResult("REJECTED");
            claim.setRejectionReason("Claim timed out after " + claimTimeoutSeconds + " seconds");
            bingoClaimRepository.save(claim);
        }

        // Skip the resume if a concurrent decide then won the race under the lock.
        long remaining = bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (remaining > 0) {
            log.info("Game {}: not resumed by claim timeout — other claims still pending.", gameId);
            return;
        }

        // Nothing left to review: settle any winner already confirmed during this
        // round (an auto-approved claim must still be paid when its unresolvable
        // companion claim times out); with no winner this resumes the countdown.
        settleApprovedWinners(locked, locked.getAdminUserId());

        log.info("Game {}: All timed-out claims rejected, game settled or resuming in {} seconds.",
                gameId, COUNTDOWN_SECONDS);
    }

    /**
     * Stop automatic number calling for a game
     */
    public void stopCalling(Long gameId) {
        ScheduledFuture<?> future = activeGameTasks.remove(gameId);
        if (future != null) {
            future.cancel(false);
            log.info("Stopped number calling for game {}", gameId);
        }
        gameTenantContexts.remove(gameId);
    }

    /**
     * Wrapper method for calling next number (compatible with AdminBotHandler)
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public Integer callNumber(Long gameId) {
        return callNextNumber(gameId);
    }

    /**
     * Call the next number in the sequence
     */
    public Integer callNextNumber(Long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            // STARTING is only ever observed here as a stale read of a status change
            // that is still mid-transaction (the scheduleAtFixedRate first tick already
            // waits one `interval` for the commit, but belt-and-braces never hurt);
            // do NOT cancel the scheduler for it or number calling dies permanently.
            // Every other status means calling is legitimately over (paused / claim
            // pending / registration open / ended), so shut the caller down.
            if (game.getStatus() != GameStatus.STARTING) {
                log.debug("Game {} is not calling ({}), stopping caller", gameId, game.getStatus());
                stopCalling(gameId);
            }
            return null;
        }

        int currentIndex = game.getCurrentCallIndex();

        // Check if game has ended (all 75 numbers called)
        if (currentIndex >= 75) {
            endGameWithoutWinner(gameId, "All numbers called, no winner");
            return null;
        }

        // Get next number from sealed sequence
        CalledNumber nextNumber = calledNumberRepository
                .findByGameIdAndSequenceIndex(gameId, currentIndex)
                .orElseThrow(() -> new RuntimeException("Number sequence not found"));

        // Update game progress
        game.setCurrentCallIndex(currentIndex + 1);
        game.setTotalNumbersCalled(game.getTotalNumbersCalled() + 1);

        // Mark as called
        nextNumber.setCalledAt(LocalDateTime.now());
        calledNumberRepository.save(nextNumber);
        gameRepository.save(game);

        // Publish WebSocket events
        publishNumberCalledEvent(gameId, nextNumber);

        log.info("Game {}: Called number {} (sequence {})", gameId, nextNumber.getNumber(), currentIndex);

        return nextNumber.getNumber();
    }

    /**
     * Call a specific number (manual override)
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public Integer callSpecificNumber(Long gameId, Integer number) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        // Find if this number has been called yet
        Optional<CalledNumber> existing = calledNumberRepository
                .findByGameIdAndNumberAndCalledAtIsNotNull(gameId, number);

        if (existing.isPresent()) {
            throw new RuntimeException("Number already called");
        }

        // Find the number in sequence and mark it as called
        CalledNumber calledNumber = calledNumberRepository
                .findByGameIdAndNumber(gameId, number)
                .orElseThrow(() -> new RuntimeException("Number not in sequence"));

        calledNumber.setCalledAt(LocalDateTime.now());
        calledNumberRepository.save(calledNumber);

        // Update game progress if this is the next number
        if (calledNumber.getSequenceIndex() == game.getCurrentCallIndex()) {
            game.setCurrentCallIndex(game.getCurrentCallIndex() + 1);
            game.setTotalNumbersCalled(game.getTotalNumbersCalled() + 1);
            gameRepository.save(game);
        }

        publishNumberCalledEvent(gameId, calledNumber);

        log.info("Game {}: Manually called number {}", gameId, number);
        return number;
    }

    /**
     * Claim Bingo for a player — allows multiple simultaneous claims.
     * The system only makes decisions it can prove. At submission time that is
     * a rejection: when the game's pattern is readable, a card on which the
     * pattern is provably incomplete can never become a real Bingo, so the
     * claim is rejected, the card banned and the game never paused; when the
     * pattern is not readable the last-called-number heuristic applies (a card
     * without the game's last called number is impossible). A claim whose
     * pattern is provably complete, and every claim the system cannot prove
     * either way, waits in the pending queue for the automatic reviewer, which
     * decides every remaining claim from server truth within seconds.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BingoClaimResult claimBingo(Long gameId, Long playerId, Long cardId, java.util.List<Integer> markedNumbers, Boolean autoMark) throws JsonProcessingException {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS && game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new GameProgressException("Game is not accepting claims",
                    "Bingo can't be claimed right now.");
        }

        GameCard gameCard = resolveClaimCard(gameId, playerId, cardId);

        if (gameCard.isBanned()) {
            throw new GameProgressException("Card banned from game " + gameId,
                    "Your card #" + gameCard.getCard().getId() + " was banned in this game.");
        }

        if (bingoClaimRepository.existsByGameIdAndCardIdAndResult(gameId, gameCard.getCard().getId(), "VALID")) {
            throw new RequestAlreadyProcessedException(
                    "Card #" + gameCard.getCard().getId() + " already claimed Bingo in game " + gameId);
        }

        // A player holding multiple cards may only claim once per game: a second
        // claim on another card would stack pending claims and double their share
        // when the admin approves all simultaneous winners.
        if (bingoClaimRepository.existsByGameIdAndPlayerIdAndResult(gameId, playerId, "VALID")) {
            throw new RequestAlreadyProcessedException(
                    "Player already claimed Bingo in game " + gameId + " with another card");
        }

        // Get called numbers so far
        List<Integer> calledNumbers = calledNumberRepository
                .findCalledNumbersByGameId(gameId);

        // Get card numbers
        Card card = gameCard.getCard();

        // Persist the player's auto-mark preference for this card if sent with the claim.
        if (autoMark != null) {
            gameCard.setAutoMark(autoMark);
            gameCardRepository.save(gameCard);
        }

        // Two certain rejections can happen here, in order of reliability:
        //  - the pattern is readable and the card provably does not complete it
        //    with the numbers called so far — no future call can undo a claim
        //    that was already impossible when it was made;
        //  - the pattern is not readable — the legacy heuristic: a card that
        //    does not even contain the game's last called number cannot hold a
        //    real Bingo.
        // A provably complete pattern skips both checks: the win is real however
        // late the player claims, and waits for the automatic reviewer to confirm
        // it. Unreadable card data proves nothing and stays in the queue for the
        // reviewer to reject from the registered card.
        Integer lastCalledNumber = calledNumbers.isEmpty()
                ? null
                : calledNumbers.get(calledNumbers.size() - 1);
        int[][] cardNumbers = parseCardNumbersOrNull(card.getNumbers());
        boolean patternDecidable = cardNumbers != null && cardGridIsSane(cardNumbers)
                && isRecognizedPattern(game.getWinningPattern());
        if (patternDecidable && !calledNumbers.isEmpty()) {
            if (!validateBingo(cardNumbers, calledNumbers, game.getWinningPattern())) {
                return rejectImpossibleClaim(game, gameCard, card, calledNumbers,
                        "Pattern " + game.getWinningPattern()
                                + " is not complete with the numbers called so far");
            }
        } else if (lastCalledNumber != null && cardNumbers != null
                && !cardContainsNumber(cardNumbers, lastCalledNumber)) {
            return rejectImpossibleClaim(game, gameCard, card, calledNumbers,
                    "Card does not contain the last called number (" + lastCalledNumber + ")");
        }

        // Every claim that survived the certain checks is left pending for the
        // automatic reviewer to decide.

        // Pause on first claim only
        boolean firstClaim = game.getStatus() == GameStatus.IN_PROGRESS;
        if (firstClaim) {
            stopCalling(gameId);
            game.setStatus(GameStatus.CLAIM_PENDING);
            gameRepository.save(game);
            publishGameStatusEvent(gameId, GameStatus.CLAIM_PENDING);
        }

        BingoClaim claim = BingoClaim.builder()
                .gameId(gameId)
                .playerId(playerId)
                .cardId(card.getId())
                .cardSnapshot(card.getNumbers())
                .calledNumbersSnapshot(objectMapper.writeValueAsString(calledNumbers))
                .result("VALID")
                .claimedAt(LocalDateTime.now())
                .build();
        bingoClaimRepository.save(claim);

        notifyAdminOfClaim(game, playerId, claim);

        publishClaimPendingEvent(gameId, claim);

        log.info("Game {}: Bingo claimed by player {} (claimId={}), first={}",
                gameId, playerId, claim.getId(), firstClaim);

        return BingoClaimResult.builder()
                .valid(true)
                .claimId(claim.getId())
                .pendingReview(true)
                .rewardAmount(BigDecimal.ZERO)
                .build();
    }

    /**
     * Resolve which of the player's cards a claim/daub applies to.
     * Null cardId (legacy clients) falls back to their first non-banned card.
     */
    private GameCard resolveClaimCard(Long gameId, Long playerId, Long cardId) {
        if (cardId != null) {
            return gameCardRepository.findByGameIdAndCardId(gameId, cardId)
                    .filter(gc -> gc.getPlayerId().equals(playerId))
                    .orElseThrow(() -> new GameProgressException("Card not held in this game",
                            "You don't hold this card in this game."));
        }
        return gameCardRepository.findAllByGameIdAndPlayerId(gameId, playerId).stream()
                .filter(gc -> !gc.isBanned())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Player not registered for this game"));
    }

    /**
     * Approve ALL pending claims as simultaneous winners (same call state).
     * The pot is shared equally between however many distinct players claimed;
     * there is no cap on winners. The game ends and every winner's card is marked.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BingoClaimResult approveAllClaims(Long gameId, Long adminId) {
        Game game = lockOwnedGame(gameId, adminId);

        List<BingoClaim> pending = bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (pending.isEmpty()) {
            throw new GameProgressException("No pending claims for game " + gameId,
                    "There are no claims waiting for review.");
        }

        // Atomically lock every claim — any that lose a race are dropped.
        // A player may only win one share: skip a second claim from the same player.
        LocalDateTime now = LocalDateTime.now();
        List<BingoClaim> locked = new java.util.ArrayList<>();
        for (BingoClaim c : dedupeByPlayer(pending)) {
            if (bingoClaimRepository.claimForProcessing(c.getId(), adminId, now) == 1) {
                // Keep the entity in step with the lock: the bulk update does not touch
                // the persistence context, so the payout save below must not write the
                // stale validatedAt = null back over the approval.
                c.setValidatedBy(adminId);
                c.setValidatedAt(now);
                locked.add(c);
            }
        }
        if (locked.isEmpty()) {
            throw new GameProgressException("No claims could be locked for processing",
                    "No pending claims could be approved. Try again.");
        }

        return settleApprovedWinners(game, adminId, locked);
    }

    /**
     * Approve ONE pending claim as a winner. The game is not paid out yet: the
     * remaining claims still have to be reviewed, and the pot is shared equally
     * between every winner. Payout therefore happens when the last pending claim
     * of this game is resolved — approved (see {@link #settleApprovedWinners}) or
     * rejected. If no claim is approved at all, the game resumes as before.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BingoClaimResult approveClaim(Long gameId, Long claimId, Long adminId) {
        Game game = lockOwnedGame(gameId, adminId);

        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new RequestAlreadyProcessedException("Game is not in CLAIM_PENDING state");
        }

        List<BingoClaim> pending = bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (pending.isEmpty()) {
            throw new GameProgressException("No pending claims for game " + gameId,
                    "There are no claims waiting for review.");
        }
        BingoClaim claim = bingoClaimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));
        if (!claim.getGameId().equals(gameId)) {
            throw new RuntimeException("Claim does not belong to this game");
        }
        boolean stillPending = pending.stream().anyMatch(c -> c.getId().equals(claimId));
        if (!stillPending) {
            throw new RequestAlreadyProcessedException("Claim already processed");
        }

        LocalDateTime now = LocalDateTime.now();
        if (bingoClaimRepository.claimForProcessing(claimId, adminId, now) == 0) {
            throw new RequestAlreadyProcessedException("Claim already processed");
        }

        // One player can only ever take one share: a second card from an already
        // confirmed winner is rejected, exactly like a duplicate claim in a bulk approval.
        boolean duplicateWinner = alreadyApprovedWinner(gameId, claim.getPlayerId(), claimId);
        if (duplicateWinner) {
            return rejectDuplicateClaim(game, claimId, adminId,
                    "This player already wins a share of this game");
        }

        // Keep the in-memory claim in step with the lock: the bulk update above does not
        // touch the persistence context, so a later save must not write the stale
        // validatedAt = null back over the approval.
        claim.setValidatedBy(adminId);
        claim.setValidatedAt(now);
        claim.setResult("VALID");
        bingoClaimRepository.save(claim);

        boolean morePending = !bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID").isEmpty();

        log.info("Game {}: Admin {} approved claim {} (player {}). {} claim(s) still pending.",
                gameId, adminId, claimId, claim.getPlayerId(), morePending ? "1+" : "0");

        // Every winner shares the pot equally, so the shares can only be settled once
        // the admin is done reviewing this round.
        if (morePending) {
            publishClaimResolvedEvent(gameId, GameStatus.CLAIM_PENDING);
            return BingoClaimResult.builder()
                    .valid(true)
                    .claimId(claimId)
                    .pendingReview(true)
                    .gameEnded(false)
                    .approvedCount(1)
                    .rewardAmount(java.math.BigDecimal.ZERO)
                    .build();
        }
        return settleApprovedWinners(game, adminId);
    }

    /**
     * Every claim of a round was rejected, so there is no winner to pay: tell the
     * room the round is over, then bring the game back with the shared countdown so
     * nobody is left staring at a stalled board.
     */
    private BingoClaimResult resumeAfterAllClaimsResolved(Game game, Long adminId) {
        Long gameId = game.getId();
        game.setStatus(GameStatus.CLAIM_PENDING);
        gameRepository.save(game);
        publishClaimResolvedEvent(gameId, GameStatus.CLAIM_PENDING);
        resumeWithCountdown(gameId, game, REASON_CLAIM_RESOLVED);
        log.info("Game {}: all claims rejected, game resuming in {} seconds.", gameId, COUNTDOWN_SECONDS);
        return BingoClaimResult.builder()
                .valid(false)
                .pendingReview(false)
                .gameEnded(false)
                .approvedCount(0)
                .rewardAmount(java.math.BigDecimal.ZERO)
                .build();
    }

    private BingoClaimResult settleApprovedWinners(Game game, Long adminId) {
        return settleApprovedWinners(game, adminId,
                bingoClaimRepository.findApprovedUnpaidWinners(game.getId()));
    }

    /**
     * Pay out the confirmed winners of a game and end it. Every approved claim is
     * one simultaneous winner and they take the game's prize in equal shares, no
     * matter how many cards claimed: two claimants split the prize two ways, three
     * split it three ways. The admin keeps the rest of the pot as commission, and
     * the owner a share of that. With no approved winner the game simply resumes.
     *
     * <p>Guarded by the game row lock and by the {@code CLAIM_PENDING} status, so the
     * commission can only ever be taken and the prize only ever be paid once.
     *
     * @param approved the confirmed winners of this game. Callers that just locked the
     *                 claims pass them in directly; callers that resolve a claim in a
     *                 later transaction re-read the confirmed winners instead.
     */
    private BingoClaimResult settleApprovedWinners(Game game, Long adminId, List<BingoClaim> approved) {
        Long gameId = game.getId();
        List<BingoClaim> winners = dedupeByPlayer(approved);
        if (winners.isEmpty()) {
            // Every claim was rejected: nothing to pay, so play carries on.
            return resumeAfterAllClaimsResolved(game, adminId);
        }

        int shareCount = winners.size();
        BigDecimal prizePool = game.getPrizePool();
        // The admin committed to a prize, not to a percentage, so the winners get
        // that figure and the admin keeps the rest of the pot as commission.
        BigDecimal prize = prizeRules.prizeFor(game);
        BigDecimal grossCommission = commissionFor(game);
        BigDecimal ownerShare = ownerShareFor(grossCommission);
        walletService.creditAgentCommission(game.getAdminUserId(), grossCommission, gameId);
        if (ownerShare.signum() > 0) {
            walletService.accrueOwnerFee(ownerShare, gameId);
        }

        BigDecimal[] shares = splitEvenly(prize, shareCount);
        for (int i = 0; i < shareCount; i++) {
            BingoClaim winner = winners.get(i);
            winner.setRewardAmount(shares[i]);
            bingoClaimRepository.save(winner);
            walletService.creditWinnings(winner.getPlayerId(), shares[i], gameId);
            if (winner.getCardId() != null) {
                gameCardRepository.findByGameIdAndCardId(gameId, winner.getCardId()).ifPresent(gc -> {
                    gc.setWinner(true);
                    gameCardRepository.save(gc);
                });
                cardService.markCardAsWinner(gameId, winner.getCardId());
            }
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);
        stopCalling(gameId);
        publishGameStatusEvent(gameId, GameStatus.ENDED);

        log.info("Game {}: Admin {} settled {} simultaneous winner(s): prize {} from a pot of {}, "
                        + "each paid {}. Game ended.",
                gameId, adminId, shareCount, prize, prizePool, shares[0]);

        return BingoClaimResult.builder()
                .valid(true)
                .pendingReview(false)
                .gameEnded(true)
                .approvedCount(shareCount)
                .rewardAmount(shares[0])
                .build();
    }

    /** Load the game under its row lock and confirm this admin owns it. */
    private Game lockOwnedGame(Long gameId, Long adminId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.getAdminUserId().equals(adminId)) {
            throw new GameProgressException("Game does not belong to this admin",
                    "This game does not belong to you.");
        }
        return game;
    }

    /** Keep only the first claim per player — one player can only win one share. */
    private List<BingoClaim> dedupeByPlayer(List<BingoClaim> claims) {
        java.util.Set<Long> players = new java.util.HashSet<>();
        List<BingoClaim> unique = new java.util.ArrayList<>();
        for (BingoClaim c : claims) {
            if (players.add(c.getPlayerId())) {
                unique.add(c);
            }
        }
        return unique;
    }

    /**
     * True when this player already holds a confirmed, unpaid winner's claim other
     * than the one being reviewed — the claim under review has just been locked by
     * this transaction and is excluded so it cannot be mistaken for a second card.
     */
    private boolean alreadyApprovedWinner(Long gameId, Long playerId, Long excludeClaimId) {
        return bingoClaimRepository.findApprovedUnpaidWinners(gameId).stream()
                .anyMatch(c -> c.getPlayerId().equals(playerId) && !c.getId().equals(excludeClaimId));
    }

    /**
     * A second card from a player who already wins a share is voided: the card is
     * banned, exactly like a rejected claim, and the game settles on the winners
     * that remain.
     */
    private BingoClaimResult rejectDuplicateClaim(Game game, Long claimId, Long adminId, String reason) {
        BingoClaim duplicate = bingoClaimRepository.findById(claimId).orElse(null);
        if (duplicate != null) {
            duplicate.setResult("REJECTED");
            duplicate.setRejectionReason(reason);
            duplicate.setValidatedBy(adminId);
            bingoClaimRepository.save(duplicate);
            if (duplicate.getCardId() != null) {
                gameCardRepository.findById(duplicate.getCardId()).ifPresent(gc -> {
                    gc.setBanned(true);
                    gameCardRepository.save(gc);
                });
            }
            notifyCardOwnerOfBan(game.getId(), duplicate);
        }
        log.info("Game {}: claim {} voided as a duplicate claim. Reason: {}",
                game.getId(), claimId, reason);
        return settleApprovedWinners(game, adminId);
    }

    /** Exact cent-perfect even split; earlier winners absorb the rounding remainder. */
    BigDecimal[] splitEvenly(BigDecimal total, int n) {
        long cents = total.movePointRight(2).setScale(0, java.math.RoundingMode.DOWN).longValueExact();
        long base = cents / n;
        long remainder = cents % n;
        BigDecimal[] parts = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            parts[i] = BigDecimal.valueOf(base + (i < remainder ? 1 : 0), 2);
        }
        return parts;
    }

    /**
     * Reject a pending Bingo claim — game resumes only when no claims remain
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void rejectClaim(Long gameId, Long claimId, Long adminId, String reason) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            throw new RequestAlreadyProcessedException("Game is not in CLAIM_PENDING state");
        }

        BingoClaim claim = bingoClaimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));

        if (!claim.getGameId().equals(gameId)) {
            throw new RuntimeException("Claim does not belong to this game");
        }

        // Atomically claim — concurrent approvals/rejections lose here
        LocalDateTime rejectedAt = LocalDateTime.now();
        int claimed = bingoClaimRepository.claimForProcessing(claimId, adminId, rejectedAt);
        if (claimed == 0) {
            throw new RequestAlreadyProcessedException("Claim already processed");
        }
        claim.setValidatedBy(adminId);
        claim.setValidatedAt(rejectedAt);

        // Mark claim as rejected
        claim.setResult("REJECTED");
        claim.setRejectionReason(reason);
        bingoClaimRepository.save(claim);

        // An admin-rejected claim bans only the claimed card; any other card the
        // player holds in the game keeps playing.
        if (claim.getCardId() != null) {
            gameCardRepository.findById(claim.getCardId()).ifPresent(gc -> {
                gc.setBanned(true);
                gameCardRepository.save(gc);
            });
        }
        log.info("Game {}: Card {} banned (claim {} by player {} rejected).",
                gameId, claim.getCardId(), claimId, claim.getPlayerId());

        notifyCardOwnerOfBan(gameId, claim);

        log.info("Game {}: Admin {} rejected claim {}. Reason: {}.",
                gameId, adminId, claimId, reason);

        // Only when no claim is left to review does the round resolve: if another
        // claim was already confirmed as a winner, the winners are paid their equal
        // shares now and the game ends; with no winner at all the game resumes.
        long remaining = bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (remaining == 0) {
            // No claim left to review: pay the confirmed winners, or — when the admin
            // rejected them all — bring the game back with a countdown.
            settleApprovedWinners(game, adminId);
        } else {
            publishClaimResolvedEvent(gameId, GameStatus.CLAIM_PENDING);
        }
    }

    /**
     * Automated claim review — the only way claims are ever decided. Validation
     * is fully automatic and always on: every pending claim of a round is
     * classified against server truth and acted on without an admin. A provable
     * win ({@link ClaimVerdict#WIN}) is approved and paid when the round
     * resolves; everything else — pattern incomplete, unrecognized pattern, or
     * unreadable/missing card data ({@link ClaimVerdict#LOSS}) — is rejected
     * and the card is banned. No claim is ever left undecided.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void automatedClaimReview(Long gameId, Long adminId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            return;
        }

        List<BingoClaim> pending = bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID");
        if (pending.isEmpty()) {
            return;
        }

        List<Integer> serverCalled = calledNumberRepository.findCalledNumbersByGameId(gameId);
        if (serverCalled.isEmpty()) {
            return;
        }

        for (BingoClaim claim : pending) {
            String registeredJson = registeredCardJson(game, claim);
            int[][] serverCard = parseCardNumbersOrNull(registeredJson);
            ClaimVerdict verdict = classifyClaim(game, claim, serverCalled, serverCard);
            if (verdict == ClaimVerdict.LOSS) {
                try {
                    rejectClaim(gameId, claim.getId(), adminId,
                            "auto: Pattern " + game.getWinningPattern()
                                    + " not complete on this card with called numbers");
                } catch (Exception e) {
                    log.warn("Game {}: auto-reject (LOSS) of claim {} failed: {}", gameId, claim.getId(), e.getMessage());
                }
                continue;
            }
            if (verdict == ClaimVerdict.WIN) {
                try {
                    approveClaim(gameId, claim.getId(), adminId);
                } catch (RequestAlreadyProcessedException e) {
                    log.info("Game {}: claim {} already processed while auto-approving.", gameId, claim.getId());
                } catch (Exception e) {
                    log.warn("Game {}: auto-approve of claim {} failed: {}", gameId, claim.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Reject a claim the system proved impossible (incomplete pattern, or the
     * legacy last-called-number heuristic). The claim is recorded as rejected,
     * only the card that tried to claim is banned and the owner is notified;
     * the game keeps its current status — an impossible claim never pauses it —
     * and the entry fee is not refunded.
     */
    private BingoClaimResult rejectImpossibleClaim(Game game, GameCard gameCard, Card card,
                                                   List<Integer> calledNumbers, String reason)
            throws JsonProcessingException {
        LocalDateTime now = LocalDateTime.now();

        BingoClaim claim = BingoClaim.builder()
                .gameId(game.getId())
                .playerId(gameCard.getPlayerId())
                .cardId(card.getId())
                .cardSnapshot(card.getNumbers())
                .calledNumbersSnapshot(objectMapper.writeValueAsString(calledNumbers))
                .result("REJECTED")
                .rejectionReason("auto: " + reason)
                .validatedBy(game.getAdminUserId())
                .claimedAt(now)
                .validatedAt(now)
                .build();
        bingoClaimRepository.save(claim);

        gameCard.setBanned(true);
        gameCardRepository.save(gameCard);

        notifyCardOwnerOfBan(game.getId(), claim);

        log.info("Game {}: claim {} by player {} auto-rejected and card {} banned — {}.",
                game.getId(), claim.getId(), gameCard.getPlayerId(), card.getId(), reason);

        return BingoClaimResult.builder()
                .valid(false)
                .claimId(claim.getId())
                .pendingReview(false)
                .banned(true)
                .rewardAmount(BigDecimal.ZERO)
                .build();
    }

    public enum ClaimVerdict {
        WIN, LOSS
    }

    /**
     * Decide a claim on server truth alone. Validation is fully automatic: a
     * claim is a {@linkplain ClaimVerdict#WIN win} when the registered card
     * completes the game's pattern with the numbers called so far, and a
     * {@linkplain ClaimVerdict#LOSS loss} for everything else. Missing or
     * unreadable card data, an unrecognized winning pattern, or a card the
     * server cannot parse are all losses — a claim is never left undecided
     * for a human to resolve.
     */
    private ClaimVerdict classifyClaim(Game game, BingoClaim claim, List<Integer> serverCalled,
                                      int[][] serverCard) {
        if (game == null || claim == null || serverCalled == null || serverCalled.isEmpty()
                || serverCard == null) {
            return ClaimVerdict.LOSS;
        }
        String pattern = game.getWinningPattern();
        if (!isRecognizedPattern(pattern) || !cardGridIsSane(serverCard)) {
            return ClaimVerdict.LOSS;
        }
        if (validateBingo(serverCard, serverCalled, pattern)) {
            return ClaimVerdict.WIN;
        }
        return ClaimVerdict.LOSS;
    }

    private boolean cardContainsNumber(int[][] card, int number) {
        for (int[] row : card) {
            for (int n : row) {
                if (n == number) {
                    return true;
                }
            }
        }
        return false;
    }

    private String registeredCardJson(Game game, BingoClaim claim) {
        if (claim.getCardId() == null) {
            return null;
        }
        return gameCardRepository.findByGameIdAndCardId(game.getId(), claim.getCardId())
                .map(GameCard::getCard)
                .map(Card::getNumbers)
                .orElse(null);
    }

    /**
     * End game without a winner (force end or no claims)
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void endGameWithoutWinner(Long gameId, String reason) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        // Idempotency: a repeat "End" (stale UI tap, or the game already ended on
        // its own when all 75 numbers were called) must NOT refund players twice.
        if (game.getStatus() == GameStatus.ENDED) {
            log.info("Game {}: already ended ({}); ignoring repeat end request", gameId, game.getStatus());
            return;
        }

        stopCalling(gameId);

        // Refund entry fees — but only if nobody won (winners already got prizes;
        // refunding them too would double-pay once approval-resume is in play)
        boolean hasWinner = !gameCardRepository.findByGameIdAndWinnerTrue(gameId).isEmpty();
        if (hasWinner) {
            // Winner already took the pot (minus commission); losers' fees stay with the house.
            log.info("Game {}: skipping entry-fee refunds because the game has a winner.", gameId);
        } else {
            refundAllPlayersForGame(gameId, game.getEntryFee());
        }

        game.setStatus(GameStatus.ENDED);
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

        publishGameStatusEvent(gameId, GameStatus.ENDED);

        log.info("Game {} ended without winner. Reason: {}", gameId, reason);
    }

    /**
     * Admin commission for a game: whatever the pot has left after the prize the
     * admin committed to. See {@link PrizeRules} for the bounds on that prize.
     */
    private BigDecimal commissionFor(Game game) {
        return prizeRules.commissionFor(game);
    }

    /**
     * Owner (super admin) share of the agent's commission, per the platform
     * {@code ownerShareRate} config. The agent keeps the full commission; this
     * amount only accrues as a cash debt the agent settles with the owner.
     */
    private BigDecimal ownerShareFor(BigDecimal grossCommission) {
        BigDecimal rate = configService.getOwnerFeePercent();
        return grossCommission.multiply(rate)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }


    /**
     * Validate Bingo claim — respects the game's configured winning pattern.
     *
     * <p>FULL_HOUSE needs all 24 non-free cells called. Every other code in the admin
     * picker ({@link WinningPatternGeometry#codes()}) is settled semantically by
     * {@link BingoPatternRules}: the grid shown to the player is one example of the
     * pattern, and any arrangement the code's name describes wins (any N complete
     * lines, any N complete 2x2 blocks, and so on). See the rules class for each
     * mapping. A pattern outside the canonical set asks for nothing and never wins.
     */
    boolean validateBingo(int[][] cardNumbers, List<Integer> calledNumbers, String pattern) {
        Set<Integer> calledSet = new HashSet<>(calledNumbers);
        calledSet.add(0);

        if ("FULL_HOUSE".equals(pattern)) {
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 5; col++) {
                    if (row == 2 && col == 2) continue;
                    if (!calledSet.contains(cardNumbers[row][col])) {
                        return false;
                    }
                }
            }
            return true;
        }

        return WinningPatternGeometry.has(pattern)
                && BingoPatternRules.wins(cardNumbers, calledSet, pattern);
    }

    /**
     * Parse card numbers from JSON
     */
    private int[][] parseCardNumbers(String numbersJson) {
        int[][] parsed = parseCardNumbersOrNull(numbersJson);
        return parsed != null ? parsed : new int[5][5];
    }

    /**
     * Parse card numbers from JSON, or {@code null} when the stored card cannot be
     * read. A card the server cannot parse must never be treated as a card that
     * "does not contain" a number — that would ban a player over bad server data.
     */
    private int[][] parseCardNumbersOrNull(String numbersJson) {
        if (numbersJson == null || numbersJson.isBlank()) {
            return null;
        }
        try {
            int[][] parsed = objectMapper.readValue(numbersJson, int[][].class);
            return parsed != null && parsed.length > 0 ? parsed : null;
        } catch (Exception e) {
            log.error("Failed to parse card numbers: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Every pattern the engine can decide automatically: the canonical picker set
     * (all grid-defined codes plus the whole-card house). Claims under anything
     * outside this set are rejected as losses — the set is the single source of
     * truth for {@link #isRecognizedPattern}.
     */
    static Set<String> recognizedPatternCodes() {
        Set<String> codes = new LinkedHashSet<>(WinningPatternGeometry.codes());
        codes.add("FULL_HOUSE");
        return Collections.unmodifiableSet(codes);
    }

    static boolean isRecognizedPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        return recognizedPatternCodes().contains(pattern.trim().toUpperCase());
    }

    private boolean cardGridIsSane(int[][] card) {
        if (card == null || card.length != 5) {
            return false;
        }
        for (int[] row : card) {
            if (row == null || row.length != 5) {
                return false;
            }
        }
        return true;
    }


    /**
     * Get current game state for a player
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public GameState getGameState(Long gameId, Long playerId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        List<Integer> calledNumbers = calledNumberRepository
                .findCalledNumbersByGameId(gameId);

        List<GameCard> myCards = gameCardRepository.findAllByGameIdAndPlayerId(gameId, playerId);

        List<GameStateResponse.PlayerCardView> playerCards = myCards.stream()
                .map(gc -> new GameStateResponse.PlayerCardView(
                        gc.getCard().getId(),
                        parseCardNumbers(gc.getCard().getNumbers()),
                        gc.isWinner(),
                        gc.isBanned(),
                        parseMarkedNumbers(gc),
                        gc.getAutoMark()))
                .toList();

        List<GameStateResponse.PreviewCardView> previewCards = List.of();
        if (game.getStatus() == GameStatus.REGISTRATION_OPEN) {
            previewCards = cardPreviewRepository
                    .findByGameIdAndPlayerIdOrderByCreatedAtAsc(gameId, playerId).stream()
                    .map(cp -> new GameStateResponse.PreviewCardView(
                            cp.getCard().getId(),
                            parseCardNumbers(cp.getCard().getNumbers())))
                    .toList();
        }

        boolean anyWinner = playerCards.stream().anyMatch(GameStateResponse.PlayerCardView::winner);
        // The winner's own share, so their result screen can state exactly what they won.
        // Only read once the game is over — before that a claim is not a payout.
        BigDecimal winnerReward = anyWinner && game.getStatus() == GameStatus.ENDED
                ? bingoClaimRepository.findByGameIdAndPlayerIdAndResult(gameId, playerId, "VALID")
                        .map(BingoClaim::getRewardAmount)
                        .orElse(null)
                : null;

        // Everyone who won this game, so a winner is told up front when the pot was
        // shared with other cards rather than won outright.
        int winnerCount = game.getStatus() == GameStatus.ENDED
                ? (int) bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNotNull(gameId, "VALID")
                : 0;

        Boolean firstCardPref = playerCards.stream()
                .map(GameStateResponse.PlayerCardView::autoMark)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean autoMark = firstCardPref != null
                ? firstCardPref
                : Boolean.TRUE.equals(game.getAutoMark());

        // Whole-game results, so the ended board can list every winning and every
        // banned card of the round, not just the caller's own.
        List<GameStateResponse.WinnerCardView> winnerCards;
        List<GameStateResponse.BannedCardView> bannedCards;
        if (game.getStatus() == GameStatus.ENDED) {
            List<BingoClaim> validClaims = bingoClaimRepository.findByGameIdAndResult(gameId, "VALID");
            winnerCards = gameCardRepository.findByGameIdAndWinnerTrue(gameId).stream()
                    .map(gc -> new GameStateResponse.WinnerCardView(
                            gc.getCard().getId(),
                            parseCardNumbers(gc.getCard().getNumbers()),
                            validClaims.stream()
                                    .filter(c -> c.getCardId() != null && c.getCardId().equals(gc.getCard().getId()))
                                    .filter(c -> c.getRewardAmount() != null)
                                    .map(BingoClaim::getRewardAmount)
                                    .findFirst()
                                    .orElse(null)))
                    .toList();
            bannedCards = gameCardRepository.findByGameId(gameId).stream()
                    .filter(GameCard::isBanned)
                    .map(gc -> new GameStateResponse.BannedCardView(
                            gc.getCard().getId(),
                            parseCardNumbers(gc.getCard().getNumbers())))
                    .toList();
        } else {
            winnerCards = List.of();
            bannedCards = List.of();
        }

        return GameState.builder()
                .gameId(gameId)
                .status(game.getStatus())
                .currentCallIndex(game.getCurrentCallIndex())
                .totalNumbersCalled(game.getTotalNumbersCalled())
                .calledNumbers(calledNumbers)
                .prizeAmount(game.getPrizeAmount())
                .playerCards(playerCards)
                .previewCards(previewCards)
                .hasPlayerCard(!playerCards.isEmpty())
                .isWinner(anyWinner)
                .rewardAmount(winnerReward)
                .winnerCount(winnerCount)
                .winnerCards(winnerCards)
                .bannedCards(bannedCards)
                .claimWindowEndsAt(claimWindowEndsAt(game))
                .autoMark(autoMark)
                .startTime(game.getStartTime())
                .winningPattern(game.getWinningPattern())
                .fairnessHash(game.getFairnessHash())
                .build();
    }

    /**
     * Get admin game state (game metadata + called numbers + player count)
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public AdminGameState getAdminGameState(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        List<Integer> calledNumbers = calledNumberRepository
                .findCalledNumbersByGameId(gameId);

        int playerCount = (int) gameCardRepository.countDistinctPlayersByGameId(gameId);

        List<GameStateResponse.WinnerCardView> winnerCards;
        List<GameStateResponse.BannedCardView> bannedCards;
        if (game.getStatus() == GameStatus.ENDED) {
            List<BingoClaim> validClaims = bingoClaimRepository.findByGameIdAndResult(gameId, "VALID");
            winnerCards = gameCardRepository.findByGameIdAndWinnerTrue(gameId).stream()
                    .map(gc -> new GameStateResponse.WinnerCardView(
                            gc.getCard().getId(),
                            parseCardNumbers(gc.getCard().getNumbers()),
                            validClaims.stream()
                                    .filter(c -> c.getCardId() != null && c.getCardId().equals(gc.getCard().getId()))
                                    .filter(c -> c.getRewardAmount() != null)
                                    .map(BingoClaim::getRewardAmount)
                                    .findFirst()
                                    .orElse(null)))
                    .toList();
            bannedCards = gameCardRepository.findByGameId(gameId).stream()
                    .filter(GameCard::isBanned)
                    .map(gc -> new GameStateResponse.BannedCardView(
                            gc.getCard().getId(),
                            parseCardNumbers(gc.getCard().getNumbers())))
                    .toList();
        } else {
            winnerCards = List.of();
            bannedCards = List.of();
        }

        return AdminGameState.builder()
                .game(game)
                .calledNumbers(calledNumbers)
                .playerCount(playerCount)
                .winnerCards(winnerCards)
                .bannedCards(bannedCards)
                .claimWindowEndsAt(claimWindowEndsAt(game))
                .build();
    }

    /**
     * The players' window to claim Bingo: the review deadline, computed exactly
     * as the automatic reviewer computes it — the oldest pending claim plus the
     * tenant's review grace (10s by default). Null unless the game is paused on
     * a claim, so screens only animate the countdown while it can actually be
     * acted on.
     */
    private LocalDateTime claimWindowEndsAt(Game game) {
        if (game.getStatus() != GameStatus.CLAIM_PENDING) {
            return null;
        }
        int grace = reviewGraceSeconds(game);
        return bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(game.getId(), "VALID").stream()
                .map(BingoClaim::getClaimedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .map(oldest -> oldest.plusSeconds(grace))
                .orElse(null);
    }

    private int reviewGraceSeconds(Game game) {
        if (automationConfigRepository != null) {
            Integer grace = automationConfigRepository
                    .findByAdminUserId(game.getAdminUserId())
                    .map(AutomationConfig::getReviewGraceSeconds)
                    .orElse(null);
            if (grace != null) {
                return grace;
            }
        }
        return AutomationConfig.DEFAULT_REVIEW_GRACE_SECONDS;
    }

    @lombok.Builder
    @lombok.Data
    public static class AdminGameState {
        private Game game;
        private List<Integer> calledNumbers;
        private int playerCount;
        private List<GameStateResponse.WinnerCardView> winnerCards;
        private List<GameStateResponse.BannedCardView> bannedCards;
        /**
         * When the game is paused on a claim: the moment the automatic reviewer
         * decides every pending claim. Until then players can still claim Bingo.
         * Null whenever the game is not waiting on a claim.
         */
        private java.time.LocalDateTime claimWindowEndsAt;
    }

    static java.util.List<Integer> parseMarkedNumbers(GameCard gameCard) {
        if (gameCard == null || gameCard.getMarkedNumbers() == null
                || gameCard.getMarkedNumbers().isBlank()) {
            return java.util.List.of();
        }
        try {
            return java.util.Arrays.stream(gameCard.getMarkedNumbers().split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
        } catch (NumberFormatException e) {
            return java.util.List.of();
        }
    }

    /**
     * Persist the player's manual daubs so they survive refreshes and reconnects.
     * Every mark must correspond to an already-called number (or the free spot).
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void saveMarks(Long gameId, Long playerId, Long cardId, java.util.List<Integer> markedNumbers, Boolean autoMark) {
        if (markedNumbers == null) {
            markedNumbers = java.util.List.of();
        }
        GameCard gameCard = resolveClaimCard(gameId, playerId, cardId);

        // Persist the player's auto-mark preference (null = follow the game default).
        if (autoMark != null) {
            gameCard.setAutoMark(autoMark);
            gameCardRepository.save(gameCard);
        }

        boolean effectiveAutoMark = Boolean.TRUE.equals(gameCard.getAutoMark() != null
                ? gameCard.getAutoMark()
                : gameRepository.findById(gameId).map(Game::getAutoMark).orElse(true));

        if (effectiveAutoMark && markedNumbers != null && !markedNumbers.isEmpty()) {
            throw new GameProgressException("Game uses auto-marking",
                    "Your card is set to auto-mark numbers.");
        }

        java.util.Set<Integer> allowed = new java.util.HashSet<>(
                calledNumberRepository.findCalledNumbersByGameId(gameId));
        allowed.add(0);
        for (Integer n : markedNumbers) {
            if (n == null || !allowed.contains(n)) {
                throw new GameProgressException("Number not called yet",
                        (n == null ? "A mark" : n) + " hasn't been called yet.");
            }
        }

        gameCard.setMarkedNumbers(markedNumbers.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
        gameCardRepository.save(gameCard);
    }

    /**
     * Get all called numbers for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<Integer> getCalledNumbers(Long gameId) {
        return calledNumberRepository.findCalledNumbersByGameId(gameId);
    }

    /**
     * Check if a number has been called
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean isNumberCalled(Long gameId, Integer number) {
        return calledNumberRepository.existsByGameIdAndNumberAndCalledAtIsNotNull(gameId, number);
    }

    /**
     * Pause number calling
     */
    public void pauseGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        stopCalling(gameId);
        game.setStatus(GameStatus.PAUSED);
        gameRepository.save(game);

        publishGameStatusEvent(gameId, GameStatus.PAUSED);

        log.info("Game {} paused", gameId);
    }

    /**
     * Get all pending (unresolved) valid claims for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<BingoClaimResponse> getPendingClaims(Long gameId) {
        return bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID")
                .stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    /**
     * Get the pending (unresolved) winning claims' card snapshots, so players other than
     * the claimants can inspect whether each card actually completed the game.
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<com.bingo.app.tenant.dto.response.PendingClaimCardResponse> getPendingClaimCards(Long gameId) {
        return bingoClaimRepository
                .findByGameIdAndResultAndValidatedAtIsNull(gameId, "VALID")
                .stream()
                .map(claim -> {
                    String name = "Player #" + claim.getPlayerId();
                    if (claim.getPlayerId() != null) {
                        name = userRepository.findById(claim.getPlayerId())
                                .map(this::displayName)
                                .orElse(name);
                    }
                    return com.bingo.app.tenant.dto.response.PendingClaimCardResponse.builder()
                            .claimId(claim.getId())
                            .playerId(claim.getPlayerId())
                            .playerName(name)
                            .cardId(claim.getCardId())
                            .cardNumbers(parseCardNumbers(claim.getCardSnapshot()))
                            .calledNumbers(calledNumberRepository.findCalledNumbersByGameId(gameId))
                            .claimedAt(claim.getClaimedAt())
                            .build();
                })
                .toList();
    }

    private String displayName(User user) {
        String full = String.join(" ",
                        user.getFirstName() == null ? "" : user.getFirstName(),
                        user.getLastName() == null ? "" : user.getLastName())
                .trim();
        return full.isEmpty() ? (user.getTelegramUsername() != null ? user.getTelegramUsername() : "Player") : full;
    }

    /**
     * Resume number calling, with the shared {@link #COUNTDOWN_SECONDS} warning first
     * so every player can see the game is coming back before the next number.
     */
    public void resumeGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.PAUSED) {
            throw new RuntimeException("Game is not paused");
        }

        resumeWithCountdown(gameId, REASON_RESUME);
        log.info("Game {} resuming in {} seconds", gameId, COUNTDOWN_SECONDS);
    }


    /**
     * Countdown every player gets before the numbers start — on the first call of a
     * game and on every resume alike — so nobody is caught off guard by a game that
     * suddenly starts calling again.
     */
    public static final int COUNTDOWN_SECONDS = 5;

    // Why a game is counting down; sent to clients so they can explain it to players.
    public static final String REASON_START = "start";
    public static final String REASON_RESUME = "resume";
    public static final String REASON_RESTART = "restart";
    public static final String REASON_CLAIM_RESOLVED = "claim_resolved";

    // WebSocket event publishers
    private void publishEvent(Long gameId, String type, ObjectNode data) {
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("type", type);
            event.set("data", data);
            String payload = objectMapper.writeValueAsString(event);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, payload);
        } catch (Exception e) {
            log.error("Failed to publish WebSocket event for game {}: {}", gameId, e.getMessage());
        }
    }

    private void publishNumberCalledEvent(Long gameId, CalledNumber calledNumber) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", calledNumber.getId());
        data.put("gameId", calledNumber.getGameId());
        data.put("number", calledNumber.getNumber());
        data.put("sequenceIndex", calledNumber.getSequenceIndex());
        publishEvent(gameId, "NUMBER_CALLED", data);
    }

    private void publishGameStatusEvent(Long gameId, GameStatus status) {
        publishGameStatusEvent(gameId, status, null, null);
    }

    private void publishGameStatusEvent(Long gameId, GameStatus status, java.time.LocalDateTime startTime) {
        publishGameStatusEvent(gameId, status, startTime, null);
    }

    private void publishGameStatusEvent(Long gameId, GameStatus status, java.time.LocalDateTime startTime,
                                        String reason) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("status", status.name());
        if (startTime != null) {
            data.put("startTime", startTime.toString());
        }
        if (reason != null) {
            data.put("reason", reason);
        }
        publishEvent(gameId, "GAME_STATUS_CHANGED", data);
    }

    private void publishClaimPendingEvent(Long gameId, BingoClaim claim) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("id", claim.getId());
            data.put("gameId", claim.getGameId());
            data.put("playerId", claim.getPlayerId());
            data.put("cardId", claim.getCardId());
            data.put("cardSnapshot", claim.getCardSnapshot());
            data.put("calledNumbersSnapshot", claim.getCalledNumbersSnapshot());
            data.put("result", claim.getResult());
            publishEvent(gameId, "CLAIM_PENDING", data);
        } catch (Exception e) {
            log.error("Failed to publish CLAIM_PENDING event: {}", e.getMessage());
        }
    }

    private void publishClaimResolvedEvent(Long gameId, GameStatus status) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("status", status.name());
        publishEvent(gameId, "CLAIM_RESOLVED", data);
    }

    /** Tell every player in the game that the round was voided and a fresh game starts over. */
    public void publishGameRestartedEvent(Long gameId) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("message", "The game is restarting with a fresh number sequence. "
                + "All registered players keep their cards and can play again.");
        data.put("messageKey", "game.restarted");
        publishEvent(gameId, "GAME_RESTARTED", data);
    }

    /**
     * Notify the game's admin that a player claimed Bingo so they can review it,
     * even when they are in a different part of the app.
     */
    private void notifyAdminOfClaim(Game game, Long playerId, BingoClaim claim) {
        if (game.getAdminUserId() == null) return;
        String playerName = playerName(playerId);
        try {
            notificationService.notify(
                    game.getAdminUserId(),
                    "CLAIM_PENDING",
                    "Bingo claim pending",
                    playerName + " claimed Bingo in Game #" + game.getId() + ". Review the claim.",
                    "notify.claim.pending", "{\"name\":\"" + playerName.replace("\"", "") + "\",\"gameId\":\"" + game.getId() + "\"}",
                    "GAME", game.getId(),
                    "🎯 Bingo claim!\n" + playerName + " claimed in Game #" + game.getId() + ". Open the app to approve or reject.");
        } catch (Exception e) {
            log.warn("Failed to notify admin of claim: {}", e.getMessage());
        }
    }

    /**
     * Notify the owner of a card that their Bingo claim was rejected and the
     * card is now banned for this game. The game continues with their other cards.
     */
    private void notifyCardOwnerOfBan(Long gameId, BingoClaim claim) {
        if (claim.getCardId() == null) return;
        String reason = claim.getRejectionReason() == null ? "" : claim.getRejectionReason();
        if (reason.startsWith("auto: ")) {
            reason = reason.substring("auto: ".length());
        }
        String because = reason.isBlank() ? "" : " Reason: " + reason + ".";
        try {
            notificationService.notify(
                    claim.getPlayerId(),
                    "CARD_BANNED",
                    "Card banned",
                    "Your card #" + claim.getCardId() + " was banned in Game #" + gameId +
                            "." + because + " The game continues with your other cards.",
                    "notify.card.banned",
                    "{\"cardId\":\"" + claim.getCardId() + "\",\"gameId\":\"" + gameId + "\"}",
                    "GAME", gameId,
                    "🚫 Your card #" + claim.getCardId() + " was banned in Game #" + gameId +
                            "." + because + " The game continues with your other cards.");
        } catch (Exception e) {
            log.warn("Failed to notify card owner of ban: {}", e.getMessage());
        }
    }

    private String playerName(Long playerId) {
        return userRepository.findById(playerId)
                .map(u -> {
                    if (u.getFirstName() != null && !u.getFirstName().isBlank()) {
                        return u.getFirstName() + (u.getLastName() != null && !u.getLastName().isBlank() ? " " + u.getLastName() : "");
                    }
                    return u.getTelegramUsername() != null ? u.getTelegramUsername() : "Player #" + playerId;
                })
                .orElse("Player #" + playerId);
    }

    /**
     * Refund entry fees to all registered players for a game with no winners.
     */
    private void refundAllPlayersForGame(Long gameId, BigDecimal entryFee) {
        if (entryFee == null || entryFee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<GameCard> allCards = gameCardRepository.findByGameId(gameId);
        for (GameCard card : allCards) {
            walletService.refundPlayer(card.getPlayerId(), entryFee, gameId);
        }

        log.info("Game {}: Refunded entry fee {} to {} players", gameId, entryFee, allCards.size());
    }

    @lombok.Builder
    @lombok.Data
    public static class BingoClaimResult {
        private boolean valid;
        private Long claimId;
        private boolean pendingReview;
        private boolean gameEnded;
        private int approvedCount;
        private BigDecimal rewardAmount;
        private boolean banned;
        private boolean restarted;
    }

    @lombok.Builder
    @lombok.Data
    public static class GameState {
        private Long gameId;
        private GameStatus status;
        private Integer currentCallIndex;
        private Integer totalNumbersCalled;
        private List<Integer> calledNumbers;
        private BigDecimal prizeAmount;
        private List<GameStateResponse.PlayerCardView> playerCards;
        private List<GameStateResponse.PreviewCardView> previewCards;
        private boolean hasPlayerCard;
        private boolean isWinner;
        private BigDecimal rewardAmount;
        private int winnerCount;
        private List<GameStateResponse.WinnerCardView> winnerCards;
        private List<GameStateResponse.BannedCardView> bannedCards;
        /**
         * When the game is paused on a claim: the moment the automatic reviewer
         * decides every pending claim. Until then players can still claim Bingo.
         * Null whenever the game is not waiting on a claim.
         */
        private java.time.LocalDateTime claimWindowEndsAt;
        private Boolean autoMark;
        private java.time.LocalDateTime startTime;
        private String winningPattern;
        private String fairnessHash;
    }
}
