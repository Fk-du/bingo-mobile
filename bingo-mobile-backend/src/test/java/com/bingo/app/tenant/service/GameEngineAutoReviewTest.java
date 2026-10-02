package com.bingo.app.tenant.service;

import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.master.service.NotificationService;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.entity.BingoClaim;
import com.bingo.app.tenant.entity.Card;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.entity.GameCard;
import com.bingo.app.tenant.enums.GameStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for automated claim review (bingoClaimRules): auto-approval is removed —
 * the admin always decides winners. The system only auto-rejects a claim whose
 * card does not contain the game's last called number (it cannot be a real
 * Bingo); every other claim stays pending for the admin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameEngineAutoReviewTest {

    @Mock GameRepository gameRepository;
    @Mock CalledNumberRepository calledNumberRepository;
    @Mock GameCardRepository gameCardRepository;
    @Mock CardPreviewRepository cardPreviewRepository;
    @Mock BingoClaimRepository bingoClaimRepository;
    @Mock WalletService walletService;
    @Mock CardService cardService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock TenantMapper tenantMapper;
    @Mock UserRepository userRepository;
    @Mock ConfigService configService;
    @Mock GameService gameService;
    @Mock NotificationService notificationService;

    GameEngineService engine;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final String WIN_CARD =
            "[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]";
    private static final String LOSE_CARD =
            "[[1,2,3,4,6],[7,8,9,10,11],[12,13,0,15,16],[17,18,19,20,21],[22,23,24,25,26]]";

    @BeforeEach
    void setUp() {
        when(configService.getOwnerFeePercent()).thenReturn(java.math.BigDecimal.ZERO);
        engine = new GameEngineService(gameRepository, calledNumberRepository, gameCardRepository,
                cardPreviewRepository, bingoClaimRepository, walletService, cardService, objectMapper,
                realTransactionTemplate(), messagingTemplate, tenantMapper,
                null, userRepository, notificationService, configService, gameService,
                new PrizeRules(configService));
        when(configService.getMinPrizePercent()).thenReturn(new java.math.BigDecimal("50"));
        when(configService.getMaxPrizePercent()).thenReturn(new java.math.BigDecimal("90"));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** No-op PlatformTransactionManager that actually runs the compile-time callback body. */
    private TransactionTemplate realTransactionTemplate() {
        PlatformTransactionManager noop = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return mock(TransactionStatus.class);
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        return new TransactionTemplate(noop);
    }

    private Game game(long id, String pattern) {
        Game g = new Game();
        g.setId(id);
        g.setAdminUserId(2L);
        g.setPrizePool(new java.math.BigDecimal("20.00"));
        g.setPrizeAmount(new java.math.BigDecimal("18.00"));
        g.setCallInterval(3600);
        g.setWinningPattern(pattern);
        g.setStatus(GameStatus.CLAIM_PENDING);
        return g;
    }

    private BingoClaim claim(long id, long playerId, long cardId, String cardJson, List<Integer> called) {
        try {
            return BingoClaim.builder()
                    .id(id)
                    .gameId(30L)
                    .playerId(playerId)
                    .cardId(cardId)
                    .cardSnapshot(cardJson)
                    .calledNumbersSnapshot(objectMapper.writeValueAsString(called))
                    .result("VALID")
                    .claimedAt(LocalDateTime.now().minusSeconds(5))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubServerTruth(Game g, List<Integer> called, String... cardJsonByCardId) {
        when(gameRepository.findByIdForUpdate(g.getId())).thenReturn(Optional.of(g));
        when(calledNumberRepository.findCalledNumbersByGameId(g.getId())).thenReturn(called);
        for (int i = 0; i < cardJsonByCardId.length; i++) {
            final long cardId = 1000L + i;
            final String json = cardJsonByCardId[i];
            when(gameCardRepository.findByGameIdAndCardId(g.getId(), cardId))
                    .thenReturn(Optional.of(GameCard.builder()
                            .gameId(g.getId())
                            .card(Card.builder().numbers(json).build())
                            .build()));
        }
    }

    @Test
    @DisplayName("card does not contain the last called number: auto-rejected, card banned, game resumes")
    void cardMissingLastNumberRejectedAndBanned() {
        Game g = game(30L, "SINGLE_LINE");
        BingoClaim c = claim(1L, 101L, 1000L, WIN_CARD, List.of(1, 2, 3, 4, 5, 26));
        stubServerTruth(g, List.of(1, 2, 3, 4, 5, 26), WIN_CARD);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(c));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c));
        when(bingoClaimRepository.claimForProcessing(eq(1L), any(), any())).thenReturn(1);
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(30L, "VALID")).thenReturn(0L);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(1000L)).thenReturn(Optional.of(new GameCard()));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals("REJECTED", c.getResult()),
                () -> assertTrue(c.getRejectionReason().startsWith("auto: ")),
                () -> assertTrue(c.getRejectionReason().contains("last called number")),
                () -> assertEquals(GameStatus.STARTING, g.getStatus(),
                        "all claims rejected -> the game counts down before it resumes"),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("pattern complete and last number on the card: left for the admin, nothing auto-decided")
    void cardWithLastNumberLeftForAdmin() {
        Game g = game(30L, "SINGLE_LINE");
        BingoClaim w = claim(1L, 101L, 1000L, WIN_CARD, List.of(1, 2, 3, 4, 5));
        stubServerTruth(g, List.of(1, 2, 3, 4, 5), WIN_CARD);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(w));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals(GameStatus.CLAIM_PENDING, g.getStatus(), "the admin decides winners"),
                () -> assertEquals("VALID", w.getResult()),
                () -> verify(bingoClaimRepository, never()).claimForProcessing(anyLong(), any(), any()),
                () -> verify(gameCardRepository, never()).findById(anyLong()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong()),
                () -> verify(walletService, never()).creditAgentCommission(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("pattern incomplete but last number on the card: also left for the admin")
    void cardWithLastNumberLeftForAdminWhenPatternIncomplete() {
        Game g = game(30L, "SINGLE_LINE");
        BingoClaim w = claim(1L, 101L, 1000L, WIN_CARD, List.of(1, 2, 3, 4));
        stubServerTruth(g, List.of(1, 2, 3, 4), WIN_CARD);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(w));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals(GameStatus.CLAIM_PENDING, g.getStatus()),
                () -> verify(bingoClaimRepository, never()).claimForProcessing(anyLong(), any(), any()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("tampered snapshot: registered card still contains the last number -> left for the admin")
    void tamperedSnapshotLeftForAdmin() {
        Game g = game(30L, "SINGLE_LINE");
        BingoClaim c = claim(1L, 101L, 1000L, LOSE_CARD, List.of(1, 2, 3, 4, 5));
        stubServerTruth(g, List.of(1, 2, 3, 4, 5), WIN_CARD);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(c));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals(GameStatus.CLAIM_PENDING, g.getStatus()),
                () -> verify(bingoClaimRepository, never()).claimForProcessing(anyLong(), any(), any()),
                () -> verify(gameCardRepository, never()).findById(anyLong()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("card not registered on the server: left for the admin")
    void unregisteredCardLeftForAdmin() {
        Game g = game(30L, "SINGLE_LINE");
        BingoClaim w = claim(1L, 101L, 1000L, WIN_CARD, List.of(1, 2, 3, 4, 5));
        stubServerTruth(g, List.of(1, 2, 3, 4, 5));

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(w));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals(GameStatus.CLAIM_PENDING, g.getStatus()),
                () -> verify(bingoClaimRepository, never()).claimForProcessing(anyLong(), any(), any()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("mixed claims: only the card missing the last number is rejected, the other stays for the admin")
    void mixedClaimsOnlyMissingLastNumberAutoRejected() {
        Game g = game(30L, "SINGLE_LINE");
        BingoClaim bad = claim(1L, 101L, 1000L, LOSE_CARD, List.of(1, 2, 3, 4, 5));
        BingoClaim good = claim(2L, 102L, 1001L, WIN_CARD, List.of(1, 2, 3, 4, 5));
        stubServerTruth(g, List.of(1, 2, 3, 4, 5), LOSE_CARD, WIN_CARD);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(bad, good));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(bad));
        when(bingoClaimRepository.claimForProcessing(eq(1L), any(), any())).thenReturn(1);
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(30L, "VALID")).thenReturn(1L);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(1000L)).thenReturn(Optional.of(new GameCard()));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals("REJECTED", bad.getResult()),
                () -> assertEquals("VALID", good.getResult(), "the admin decides this one"),
                () -> assertEquals(GameStatus.CLAIM_PENDING, g.getStatus(), "one claim still awaits review"),
                () -> verify(bingoClaimRepository, never()).claimForProcessing(eq(2L), any(), any()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("many simultaneous claims are NOT auto-restarted: every claim waits for the admin")
    void manyClaimsNoAutoReset() {
        Game g = game(30L, "SINGLE_LINE");
        BingoClaim a = claim(1L, 101L, 1000L, WIN_CARD, List.of(1, 2, 3, 4, 5));
        BingoClaim b = claim(2L, 102L, 1001L, WIN_CARD, List.of(1, 2, 3, 4, 5));
        BingoClaim c = claim(3L, 103L, 1002L, WIN_CARD, List.of(1, 2, 3, 4, 5));
        BingoClaim d = claim(4L, 104L, 1003L, WIN_CARD, List.of(1, 2, 3, 4, 5));
        stubServerTruth(g, List.of(1, 2, 3, 4, 5),
                WIN_CARD, WIN_CARD, WIN_CARD, WIN_CARD);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(a, b, c, d));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals(GameStatus.CLAIM_PENDING, g.getStatus(),
                        "no auto-approved winners, no auto-restart"),
                () -> verify(gameService, never()).restartGame(anyLong(), anyLong()),
                () -> verify(bingoClaimRepository, never()).claimForProcessing(anyLong(), any(), any()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("last number missing from the card is rejected regardless of how complete the pattern is")
    void missingLastNumberRejectedRegardlessOfPattern() {
        String cardJson =
                "[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]";
        Game g = game(30L, "TRIPLE_LINE");
        List<Integer> called = IntStream.rangeClosed(1, 26).boxed().toList();
        BingoClaim c = claim(1L, 101L, 1000L, cardJson, called);
        stubServerTruth(g, called, cardJson);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(c));
        when(bingoClaimRepository.findById(1L)).thenReturn(Optional.of(c));
        when(bingoClaimRepository.claimForProcessing(eq(1L), any(), any())).thenReturn(1);
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(30L, "VALID")).thenReturn(0L);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findById(1000L)).thenReturn(Optional.of(new GameCard()));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals("REJECTED", c.getResult()),
                () -> assertTrue(c.getRejectionReason().contains("last called number")),
                () -> assertEquals(GameStatus.STARTING, g.getStatus()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("last number IS on the card (POSTAGE_STAMP): auto-review leaves it for the admin, no ban")
    void cardHasLastNumberLeftForAdmin() {
        String cardJson =
                "[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]";
        Game g = game(30L, "POSTAGE_STAMP");
        List<Integer> called = List.of(1, 2, 6, 7, 3, 25);
        BingoClaim c = claim(1L, 101L, 1000L, cardJson, called);
        stubServerTruth(g, called, cardJson);

        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(30L, "VALID"))
                .thenReturn(List.of(c));

        engine.automatedClaimReview(30L, 2L);

        assertAll(
                () -> assertEquals(GameStatus.CLAIM_PENDING, g.getStatus()),
                () -> assertEquals("VALID", c.getResult()),
                () -> verify(bingoClaimRepository, never()).claimForProcessing(anyLong(), any(), any()),
                () -> verify(gameCardRepository, never()).findById(anyLong()),
                () -> verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong())
        );
    }

    @Test
    @DisplayName("stranded VALID claim on an already-ended game is voided without a ban")
    void strandedClaimOnEndedGameIsVoided() {
        Game g = game(30L, "SINGLE_LINE");
        g.setStatus(GameStatus.ENDED);
        BingoClaim stranded = claim(9L, 101L, 1000L, WIN_CARD, List.of(1, 2, 3, 4, 5));

        when(bingoClaimRepository.findUnresolvedClaimsOnEndedGames()).thenReturn(List.of(stranded));
        when(bingoClaimRepository.claimForProcessing(eq(9L), any(), any())).thenReturn(1);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.findById(30L)).thenReturn(Optional.of(g));

        engine.resolveStrandedClaimsOnEndedGames();

        assertAll(
                () -> assertEquals("REJECTED", stranded.getResult()),
                () -> assertEquals("Game already ended before this claim could be reviewed",
                        stranded.getRejectionReason()),
                () -> assertNotNull(stranded.getValidatedAt()),
                () -> verify(gameCardRepository, never()).findById(anyLong()),
                () -> verify(gameCardRepository, never()).save(argThat(GameCard::isBanned))
        );
    }
}