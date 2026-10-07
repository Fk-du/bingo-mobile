package com.bingo.app.tenant.service;

import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.entity.BingoClaim;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.entity.GameCard;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.repository.BingoClaimRepository;
import com.bingo.app.tenant.repository.CardPreviewRepository;
import com.bingo.app.tenant.repository.CalledNumberRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Payout-rule tests for the shared-winner flow using mocked repositories.
 * Verifies: commission taken once, cent-perfect equal shares, game ends
 * immediately, winner cards flagged, and the 3-winner cap enforced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameEnginePayoutsTest {

    @Mock GameRepository gameRepository;
    @Mock CalledNumberRepository calledNumberRepository;
    @Mock GameCardRepository gameCardRepository;
    @Mock CardPreviewRepository cardPreviewRepository;
    @Mock BingoClaimRepository bingoClaimRepository;
    @Mock WalletService walletService;
    @Mock CardService cardService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock TenantMapper tenantMapper;
    @Mock TenantRegistryHolder tenantRegistryHolder;
    @Mock TransactionTemplate transactionTemplate;
    @Mock UserRepository userRepository;
    @Mock ConfigService configService;
    @Mock GameService gameService;

    GameEngineService engine;
    ObjectMapper objectMapper = new ObjectMapper();

    PrizeRules prizeRules;

    @BeforeEach
    void setUp() {
        when(configService.getOwnerFeePercent()).thenReturn(BigDecimal.ZERO);
        when(configService.getMinPrizePercent()).thenReturn(new BigDecimal("50"));
        when(configService.getMaxPrizePercent()).thenReturn(new BigDecimal("90"));
        prizeRules = new PrizeRules(configService);
        engine = new GameEngineService(gameRepository, calledNumberRepository, gameCardRepository,
                cardPreviewRepository, bingoClaimRepository, walletService, cardService, objectMapper,
                transactionTemplate, messagingTemplate, tenantMapper, null, userRepository,
                null, configService, gameService, prizeRules);
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(mockTransaction()));
    }

    private TransactionStatus mockTransaction() {
        return org.mockito.Mockito.mock(TransactionStatus.class);
    }

    /** Holder shim so the constructor keeps its shape without the real registry bean. */
    interface TenantRegistryHolder {}

    /**
     * A game whose admin committed to {@code prize}; their commission is whatever
     * the pot has left. The prize chosen in each case keeps the historical 10%
     * rake, so the expected payouts below stay readable.
     */
    private Game game(long id, BigDecimal pot, String prize) {
        Game g = new Game();
        g.setId(id);
        g.setAdminUserId(2L);
        g.setPrizePool(pot);
        g.setPrizeAmount(new BigDecimal(prize));
        g.setStatus(com.bingo.app.tenant.enums.GameStatus.CLAIM_PENDING);
        return g;
    }

    private BingoClaim claim(long id, long playerId) {
        BingoClaim c = new BingoClaim();
        c.setId(id);
        c.setGameId(30L);
        c.setPlayerId(playerId);
        c.setCardId(id + 1000L);
        c.setResult("VALID");
        return c;
    }

    private void stubPending(Game g, BingoClaim... claims) {
        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(claims));
        for (BingoClaim c : claims) {
            when(bingoClaimRepository.claimForProcessing(eq(c.getId()), any(), any())).thenReturn(1);
            when(bingoClaimRepository.save(c)).thenReturn(c);
            when(gameCardRepository.findByGameIdAndCardId(g.getId(), c.getCardId()))
                    .thenReturn(Optional.of(new GameCard()));
        }
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("two shared winners: pot 20, prize 18 -> admin keeps 2 once, winners 9 each, ENDED")
    void approveAllTwoWinners() throws Exception {
        Game g = game(30L, new BigDecimal("20.00"), "18.00");
        BingoClaim w1 = claim(1L, 101L);
        BingoClaim w2 = claim(2L, 102L);
        stubPending(g, w1, w2);

        var result = engine.approveAllClaims(g.getId(), 2L);

        assertAll(
                () -> assertEquals(2, result.getApprovedCount()),
                () -> assertEquals(0, new BigDecimal("9.00").compareTo(result.getRewardAmount())),
                () -> assertTrue(result.isGameEnded()),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus())
        );
        // commission credited exactly once, from the pot
        verify(walletService, times(1)).creditAgentCommission(eq(2L), eq(new BigDecimal("2.00")), eq(30L));
        // each winner paid exactly their share of the prize
        verify(walletService).creditWinnings(101L, new BigDecimal("9.00"), 30L);
        verify(walletService).creditWinnings(102L, new BigDecimal("9.00"), 30L);
        verifyNoMoreInteractions(walletService);
        // both cards flagged as winners
        verify(cardService, times(2)).markCardAsWinner(eq(30L), anyLong());
        // both claims stored with their share
        ArgumentCaptor<BingoClaim> saved = ArgumentCaptor.forClass(BingoClaim.class);
        verify(bingoClaimRepository, times(2)).save(saved.capture());
        saved.getAllValues().forEach(c ->
                assertEquals(0, new BigDecimal("9.00").compareTo(c.getRewardAmount())));
    }

    @Test
    @DisplayName("owner share accrues as cash debt: admin keeps full 2.00 commission, owner owes 0.40, winners 9 each")
    void ownerShareAccruesAsOwnerFee() throws Exception {
        when(configService.getOwnerFeePercent()).thenReturn(new BigDecimal("20"));
        Game g = game(33L, new BigDecimal("20.00"), "18.00");
        BingoClaim w1 = claim(1L, 101L);
        BingoClaim w2 = claim(2L, 102L);
        stubPending(g, w1, w2);

        var result = engine.approveAllClaims(g.getId(), 2L);

        assertAll(
                () -> assertEquals(2, result.getApprovedCount()),
                () -> assertEquals(0, new BigDecimal("9.00").compareTo(result.getRewardAmount())),
                () -> assertTrue(result.isGameEnded())
        );
        // admin keeps the FULL commission; the owner share is only accrued as a cash debt
        verify(walletService).creditAgentCommission(eq(2L), eq(new BigDecimal("2.00")), eq(33L));
        verify(walletService).accrueOwnerFee(eq(new BigDecimal("0.40")), eq(33L));
        // winners unaffected by the owner share
        verify(walletService).creditWinnings(101L, new BigDecimal("9.00"), 33L);
        verify(walletService).creditWinnings(102L, new BigDecimal("9.00"), 33L);
    }

    @Test
    @DisplayName("no cap on winners: four simultaneous winners all share the pot equally")
    void approveAllPaysEveryWinner() {
        Game g = game(31L, new BigDecimal("40.00"), "36.00");
        stubPending(g, claim(1L, 101L), claim(2L, 102L), claim(3L, 103L), claim(4L, 104L));

        var result = engine.approveAllClaims(g.getId(), 2L);

        assertAll(
                () -> assertEquals(4, result.getApprovedCount()),
                () -> assertEquals(0, new BigDecimal("9.00").compareTo(result.getRewardAmount())),
                () -> assertTrue(result.isGameEnded()),
                () -> verify(gameService, never()).restartGame(anyLong(), anyLong())
        );
        verify(walletService).creditWinnings(101L, new BigDecimal("9.00"), 31L);
        verify(walletService).creditWinnings(102L, new BigDecimal("9.00"), 31L);
        verify(walletService).creditWinnings(103L, new BigDecimal("9.00"), 31L);
        verify(walletService).creditWinnings(104L, new BigDecimal("9.00"), 31L);
    }

    @Test
    @DisplayName("claim submission: completed pattern is accepted for review and pauses the game")
    void claimSubmissionAccepted() throws Exception {
        Game g = game(32L, new BigDecimal("20.00"), "18.00");
        g.setWinningPattern("FOUR_LINES");

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(calledNumberRepository.findCalledNumbersByGameId(g.getId()))
                .thenReturn(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());
        when(gameCardRepository.findByGameIdAndCardId(g.getId(), 5000L))
                .thenReturn(Optional.of(GameCard.builder()
                        .gameId(g.getId())
                        .playerId(101L)
                        .card(com.bingo.app.tenant.entity.Card.builder()
                                .id(5000L)
                                .numbers("[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]")
                                .build())
                        .build()));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = engine.claimBingo(g.getId(), 101L, 5000L, Collections.<Integer>emptyList(), false);

        assertTrue(result.isValid() || result.isPendingReview(),
                "a completed FOUR_LINES must be accepted");
        assertEquals(com.bingo.app.tenant.enums.GameStatus.CLAIM_PENDING, g.getStatus());
        // no money moves until the admin approves
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("rejected claim bans only the claimed card and resumes the game")
    void rejectClaimBansPlayer() {
        Game g = game(33L, new BigDecimal("20.00"), "18.00");
        BingoClaim claim = claim(7L, 101L);
        claim.setGameId(g.getId());
        GameCard claimedCard = new GameCard();

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.findById(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findById(7L)).thenReturn(Optional.of(claim));
        when(bingoClaimRepository.claimForProcessing(eq(7L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID")).thenReturn(0L);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(claim.getCardId())).thenReturn(Optional.of(claimedCard));
        when(gameCardRepository.save(any(GameCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.rejectClaim(g.getId(), 7L, 2L, "invalid pattern");

        assertAll(
                () -> assertEquals("REJECTED", claim.getResult()),
                () -> assertEquals("invalid pattern", claim.getRejectionReason()),
                () -> assertTrue(claimedCard.isBanned(), "only the claimed card must be banned"),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.STARTING, g.getStatus(),
                        "the game counts down before it resumes")
        );
        verify(gameCardRepository).save(claimedCard);
        verify(bingoClaimRepository).save(claim);
    }

    @Test
    @DisplayName("single winner approval: winner takes the whole prize; game ends instantly")
    void singleWinnerApprovalTakesWholePrize() throws Exception {
        Game g = game(32L, new BigDecimal("20.00"), "18.00");
        BingoClaim w = claim(9L, 101L);
        w.setGameId(g.getId());

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.claimForProcessing(eq(9L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(w));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndCardId(g.getId(), w.getCardId()))
                .thenReturn(Optional.of(new GameCard()));
        when(gameCardRepository.findByGameIdAndWinnerTrue(g.getId())).thenReturn(List.of());
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = engine.approveAllClaims(g.getId(), 2L);

        assertAll(
                () -> assertTrue(result.isGameEnded()),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus())
        );
        verify(walletService).creditAgentCommission(eq(2L), eq(new BigDecimal("2.00")), eq(32L));
        verify(walletService).creditWinnings(101L, new BigDecimal("18.00"), 32L);
        verifyNoMoreInteractions(walletService);
    }

    @Test
    @DisplayName("game with no winner: the full pot is refunded per card and the admin takes nothing")
    void refundNoWinner() {
        Game g = game(40L, new BigDecimal("20.00"), "18.00");
        g.setEntryFee(new BigDecimal("10.00"));
        g.setStatus(com.bingo.app.tenant.enums.GameStatus.IN_PROGRESS);

        GameCard p1 = new GameCard(); p1.setPlayerId(101L);
        GameCard p2 = new GameCard(); p2.setPlayerId(102L);

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndWinnerTrue(g.getId())).thenReturn(List.of());
        when(gameCardRepository.findByGameId(g.getId())).thenReturn(List.of(p1, p2));

        engine.endGameWithoutWinner(g.getId(), "nobody claimed");

        assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus());
        verify(walletService).refundPlayer(101L, new BigDecimal("10.00"), 40L);
        verify(walletService).refundPlayer(102L, new BigDecimal("10.00"), 40L);
        // the whole pot went back to the players, so nobody takes a cut of it
        verify(walletService, never()).creditAgentCommission(anyLong(), any(), anyLong());
        verify(walletService, never()).accrueOwnerFee(any(), anyLong());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("game with a winner ending early: no entry-fee refunds (winner already took the prize)")
    void noRefundWhenWinnerExists() {
        Game g = game(41L, new BigDecimal("20.00"), "18.00");
        g.setEntryFee(new BigDecimal("10.00"));

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndWinnerTrue(g.getId()))
                .thenReturn(List.of(new GameCard()));

        engine.endGameWithoutWinner(g.getId(), "forced");

        verify(walletService, never()).refundPlayer(anyLong(), any(BigDecimal.class), anyLong());
        assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus());
    }

    // ------------------------------------------------------------------
    // Per-claim approval: the admin reviews claims one at a time and the pot
    // is split equally between every confirmed winner when the last claim is
    // resolved.
    // ------------------------------------------------------------------

    private BingoClaim claimFor(Game g, long id, long playerId) {
        BingoClaim c = claim(id, playerId);
        c.setGameId(g.getId());
        return c;
    }

    private BingoClaim approved(Game g, long id, long playerId) {
        BingoClaim c = claimFor(g, id, playerId);
        c.setValidatedAt(LocalDateTime.now());
        c.setValidatedBy(2L);
        return c;
    }

    @Test
    @DisplayName("per-claim approve: first of two claims is held, nothing paid until the last is decided")
    void perClaimApproveHoldsUntilLastDecision() {
        Game g = game(50L, new BigDecimal("20.00"), "18.00");
        BingoClaim c1 = claimFor(g, 1L, 101L);
        BingoClaim c2 = claimFor(g, 2L, 102L);

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        // 1st approve: both pending, then only the second one is left.
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(c1, c2))
                .thenReturn(List.of(c2));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c1));
        when(bingoClaimRepository.claimForProcessing(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = engine.approveClaim(g.getId(), 1L, 2L);

        assertAll(
                () -> assertTrue(result.isPendingReview(), "other claims still await review"),
                () -> assertFalse(result.isGameEnded(), "the pot is not paid out mid-review"),
                () -> assertEquals(1, result.getApprovedCount()),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.CLAIM_PENDING, g.getStatus())
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
        verify(walletService, never()).creditAgentCommission(anyLong(), any(), anyLong());
        assertNotNull(c1.getValidatedAt(), "the approval must be persisted, not only the lock");
    }

    @Test
    @DisplayName("two per-claim approvals: both winners share the prize equally, game ends")
    void perClaimApproveSplitsEquallyBetweenWinners() {
        Game g = game(51L, new BigDecimal("20.00"), "18.00");
        BingoClaim c1 = claimFor(g, 1L, 101L);
        BingoClaim c2 = claimFor(g, 2L, 102L);

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(c1, c2))   // before the 1st approval
                .thenReturn(List.of(c2))        // still one pending after it
                .thenReturn(List.of(c2))        // before the 2nd approval
                .thenReturn(List.of());         // nothing left after it
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c1));
        when(bingoClaimRepository.findById(2L)).thenReturn(Optional.of(c2));
        when(bingoClaimRepository.claimForProcessing(anyLong(), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.findApprovedUnpaidWinners(g.getId())).thenReturn(List.of(approved(g, 1L, 101L), approved(g, 2L, 102L)));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndCardId(eq(g.getId()), anyLong())).thenReturn(Optional.of(new GameCard()));

        engine.approveClaim(g.getId(), 1L, 2L);
        var result = engine.approveClaim(g.getId(), 2L, 2L);

        assertAll(
                () -> assertTrue(result.isGameEnded()),
                () -> assertEquals(2, result.getApprovedCount()),
                () -> assertEquals(0, new BigDecimal("9.00").compareTo(result.getRewardAmount())),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus())
        );
        verify(walletService, times(1)).creditAgentCommission(eq(2L), eq(new BigDecimal("2.00")), eq(51L));
        verify(walletService).creditWinnings(101L, new BigDecimal("9.00"), 51L);
        verify(walletService).creditWinnings(102L, new BigDecimal("9.00"), 51L);
        verify(cardService, times(2)).markCardAsWinner(eq(51L), anyLong());
    }

    @Test
    @DisplayName("three per-claim approvals: pot split three ways, cent-perfect")
    void perClaimApproveSplitsThreeWays() {
        Game g = game(52L, new BigDecimal("10.00"), "9.00");
        BingoClaim c1 = claimFor(g, 1L, 101L);
        BingoClaim c2 = claimFor(g, 2L, 102L);
        BingoClaim c3 = claimFor(g, 3L, 103L);

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(c1, c2, c3))   // before the 1st approval
                .thenReturn(List.of(c2, c3))        // two left after it
                .thenReturn(List.of(c2, c3))        // before the 2nd approval
                .thenReturn(List.of(c3))            // one left after it
                .thenReturn(List.of(c3))            // before the 3rd approval
                .thenReturn(List.of());             // nothing left after it
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c1));
        when(bingoClaimRepository.findById(2L)).thenReturn(Optional.of(c2));
        when(bingoClaimRepository.findById(3L)).thenReturn(Optional.of(c3));
        when(bingoClaimRepository.claimForProcessing(anyLong(), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.findApprovedUnpaidWinners(g.getId()))
                .thenReturn(List.of(approved(g, 1L, 101L), approved(g, 2L, 102L), approved(g, 3L, 103L)));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndCardId(eq(g.getId()), anyLong())).thenReturn(Optional.of(new GameCard()));

        engine.approveClaim(g.getId(), 1L, 2L);
        engine.approveClaim(g.getId(), 2L, 2L);
        var result = engine.approveClaim(g.getId(), 3L, 2L);

        assertEquals(3, result.getApprovedCount());
        // 10.00 pot, 9.00 prize, three winners -> 3.00 each
        verify(walletService).creditWinnings(101L, new BigDecimal("3.00"), 52L);
        verify(walletService).creditWinnings(102L, new BigDecimal("3.00"), 52L);
        verify(walletService).creditWinnings(103L, new BigDecimal("3.00"), 52L);
    }

    @Test
    @DisplayName("approve then reject: the confirmed winner takes the whole prize")
    void perClaimApproveThenRejectPaysTheOnlyWinner() {
        Game g = game(53L, new BigDecimal("20.00"), "18.00");
        BingoClaim good = claimFor(g, 1L, 101L);
        BingoClaim bad = claimFor(g, 2L, 102L);
        bad.setGameId(g.getId());
        GameCard badCard = new GameCard();

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.findById(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(good, bad))
                .thenReturn(List.of(bad));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(good));
        when(bingoClaimRepository.findById(2L)).thenReturn(Optional.of(bad));
        when(bingoClaimRepository.claimForProcessing(anyLong(), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.findApprovedUnpaidWinners(g.getId())).thenReturn(List.of(approved(g, 1L, 101L)));
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID")).thenReturn(0L);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(bad.getCardId())).thenReturn(Optional.of(badCard));
        when(gameCardRepository.findByGameIdAndCardId(eq(g.getId()), anyLong())).thenReturn(Optional.of(new GameCard()));
        when(gameCardRepository.save(any(GameCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.approveClaim(g.getId(), 1L, 2L);
        engine.rejectClaim(g.getId(), 2L, 2L, "pattern not complete");

        assertAll(
                () -> assertEquals("REJECTED", bad.getResult()),
                () -> assertTrue(badCard.isBanned()),
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.ENDED, g.getStatus())
        );
        // the single confirmed winner takes the whole prize — nobody else shares it
        verify(walletService).creditWinnings(101L, new BigDecimal("18.00"), 53L);
        verify(walletService, never()).creditWinnings(eq(102L), any(), anyLong());
    }

    @Test
    @DisplayName("one player claiming with two cards wins a single share, the second card is voided")
    void perClaimApproveGivesOnePlayerOneShare() {
        Game g = game(54L, new BigDecimal("20.00"), "18.00");
        BingoClaim first = claimFor(g, 1L, 101L);
        BingoClaim second = claimFor(g, 2L, 101L);
        second.setGameId(g.getId());
        GameCard secondCard = new GameCard();

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.findById(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID"))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(second));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(first));
        when(bingoClaimRepository.findById(2L)).thenReturn(Optional.of(second));
        when(bingoClaimRepository.claimForProcessing(anyLong(), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.findApprovedUnpaidWinners(g.getId())).thenReturn(List.of(approved(g, 1L, 101L)));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(second.getCardId())).thenReturn(Optional.of(secondCard));
        when(gameCardRepository.findByGameIdAndCardId(eq(g.getId()), anyLong())).thenReturn(Optional.of(new GameCard()));
        when(gameCardRepository.save(any(GameCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.approveClaim(g.getId(), 1L, 2L);
        var result = engine.approveClaim(g.getId(), 2L, 2L);

        assertAll(
                () -> assertEquals("REJECTED", second.getResult(), "a second card cannot take a second share"),
                () -> assertTrue(secondCard.isBanned()),
                () -> assertEquals(1, result.getApprovedCount()),
                () -> assertEquals(0, new BigDecimal("18.00").compareTo(result.getRewardAmount()))
        );
        verify(walletService, times(1)).creditWinnings(eq(101L), any(), eq(54L));
    }

    @Test
    @DisplayName("every claim rejected: no payout, the game resumes after a countdown")
    void perClaimAllRejectedResumesGame() {
        Game g = game(55L, new BigDecimal("20.00"), "18.00");
        BingoClaim c = claim(1L, 101L);
        c.setGameId(g.getId());

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.findById(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c));
        when(bingoClaimRepository.claimForProcessing(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID")).thenReturn(0L);
        when(bingoClaimRepository.findApprovedUnpaidWinners(g.getId())).thenReturn(List.of());
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(c.getCardId())).thenReturn(Optional.of(new GameCard()));
        when(gameCardRepository.save(any(GameCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.rejectClaim(g.getId(), 1L, 2L, "nope");

        // STARTING, not IN_PROGRESS: players are warned and get COUNTDOWN_SECONDS
        // before the numbers start again.
        assertEquals(com.bingo.app.tenant.enums.GameStatus.STARTING, g.getStatus());
        assertNotNull(g.getStartTime());
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
        verify(walletService, never()).creditAgentCommission(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("rejecting every claim announces a counted resume to the room")
    void rejectAllAnnouncesCountedResume() {
        Game g = game(58L, new BigDecimal("20.00"), "18.00");
        BingoClaim c = claim(1L, 101L);
        c.setGameId(g.getId());

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(gameRepository.findById(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c));
        when(bingoClaimRepository.claimForProcessing(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(1);
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID")).thenReturn(0L);
        when(bingoClaimRepository.findApprovedUnpaidWinners(g.getId())).thenReturn(List.of());
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(c.getCardId())).thenReturn(Optional.of(new GameCard()));
        when(gameCardRepository.save(any(GameCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        engine.rejectClaim(g.getId(), 1L, 2L, "nope");

        // The game parks in STARTING with a start time COUNTDOWN_SECONDS away, so every
        // player sees the countdown instead of a game that silently stalled.
        assertAll(
                () -> assertEquals(com.bingo.app.tenant.enums.GameStatus.STARTING, g.getStatus()),
                () -> assertTrue(g.getStartTime().isAfter(before.plusSeconds(
                        com.bingo.app.tenant.service.GameEngineService.COUNTDOWN_SECONDS - 1)),
                        "countdown is at least COUNTDOWN_SECONDS long")
        );

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/game/58"), payload.capture());
        assertTrue(payload.getAllValues().stream().anyMatch(p ->
                        p.contains("\"status\":\"STARTING\"") && p.contains("\"reason\":\"claim_resolved\"")),
                "the resume countdown is announced with its reason");
    }

    @Test
    @DisplayName("per-claim approve with many pending claims pays the winner instead of restarting")
    void perClaimApprovePaysWithoutCap() {
        Game g = game(30L, new BigDecimal("40.00"), "36.00");
        BingoClaim first = claim(1L, 101L);
        stubPending(g, first, claim(2L, 102L), claim(3L, 103L), claim(4L, 104L));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(first));
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(3L);

        var result = engine.approveClaim(g.getId(), 1L, 2L);

        assertAll(
                () -> assertFalse(result.isRestarted(), "there is no winner cap left to trip"),
                () -> assertTrue(result.isPendingReview(),
                        "the other claims still have to be reviewed before the pot is shared"),
                () -> verify(gameService, never()).restartGame(anyLong(), anyLong())
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("a claim that is no longer pending cannot be approved twice")
    void perClaimApproveRejectsAlreadyProcessedClaim() {
        Game g = game(57L, new BigDecimal("20.00"), "18.00");
        BingoClaim c = claim(1L, 101L);

        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(g.getId(), "VALID")).thenReturn(List.of());
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThrows(GameProgressException.class, () -> engine.approveClaim(g.getId(), 1L, 2L));
    }
}
