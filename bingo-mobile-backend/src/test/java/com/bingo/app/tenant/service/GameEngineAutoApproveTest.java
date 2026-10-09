package com.bingo.app.tenant.service;

import com.bingo.app.master.entity.TenantRegistry;
import com.bingo.app.master.repository.TenantRegistryRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Automatic claim review (always on): the system decides every claim from server
 * truth and nobody approves or rejects by hand. A provably complete pattern is
 * approved (and paid when the round resolves); everything else — an incomplete
 * pattern, an unknown pattern, or unreadable/missing card data — is rejected and
 * the card banned. No claim is ever left undecided.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameEngineAutoApproveTest {

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
    @Mock TenantRegistryRepository tenantRegistryRepository;

    GameEngineService engine;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final long GAME_ID = 30L;
    private static final long ADMIN_ID = 2L;

    /** Top row 1..5 complete. */
    private static final String WIN_CARD =
            "[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]";
    /** No complete row with the calls the tests make. */
    private static final String LOSE_CARD =
            "[[1,2,3,4,6],[7,8,9,10,11],[12,13,0,15,16],[17,18,19,20,21],[22,23,24,25,26]]";

    /** The live claim ledger the repository stubs read from, mutated as claims resolve. */
    private final List<BingoClaim> claims = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(configService.getOwnerFeePercent()).thenReturn(BigDecimal.ZERO);
        when(configService.getMinPrizePercent()).thenReturn(new BigDecimal("50"));
        when(configService.getMaxPrizePercent()).thenReturn(new BigDecimal("90"));
        engine = new GameEngineService(gameRepository, calledNumberRepository, gameCardRepository,
                cardPreviewRepository, bingoClaimRepository, walletService, cardService, objectMapper,
                realTransactionTemplate(), messagingTemplate, tenantMapper,
                tenantRegistryRepository, userRepository, notificationService, configService, gameService,
                new PrizeRules(configService));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        claims.clear();
        stubClaimLedger();
    }

    /** No-op PlatformTransactionManager that actually runs the callback body. */
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

    /**
     * Repository stubs that mirror the real queries over the mutable {@link #claims}
     * list, so pending/approved/unpaid views shrink exactly as the decisions land.
     */
    private void stubClaimLedger() {
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(GAME_ID, "VALID"))
                .thenAnswer(inv -> claims.stream()
                        .filter(c -> c.getValidatedAt() == null && "VALID".equals(c.getResult()))
                        .toList());
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNull(GAME_ID, "VALID"))
                .thenAnswer(inv -> claims.stream()
                        .filter(c -> c.getValidatedAt() == null && "VALID".equals(c.getResult()))
                        .count());
        when(bingoClaimRepository.findApprovedUnpaidWinners(GAME_ID))
                .thenAnswer(inv -> claims.stream()
                        .filter(c -> c.getValidatedAt() != null && "VALID".equals(c.getResult())
                                && c.getRewardAmount() == null)
                        .toList());
        when(bingoClaimRepository.findById(anyLong()))
                .thenAnswer(inv -> claims.stream()
                        .filter(c -> c.getId().equals(inv.getArgument(0)))
                        .findFirst());
        when(bingoClaimRepository.claimForProcessing(anyLong(), any(), any())).thenReturn(1);
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gameCardRepository.findByGameIdAndCardId(eq(GAME_ID), anyLong()))
                .thenAnswer(inv -> claims.stream()
                        .filter(c -> c.getCardId() != null && c.getCardId().equals(inv.getArgument(1)))
                        .findFirst()
                        .map(c -> GameCard.builder()
                                .id(c.getCardId())
                                .gameId(GAME_ID)
                                .card(Card.builder().id(c.getCardId()).numbers(c.getCardSnapshot()).build())
                                .build()));
    }

    private Game liveGame(String pattern) {
        Game g = new Game();
        g.setId(GAME_ID);
        g.setAdminUserId(ADMIN_ID);
        g.setPrizePool(new BigDecimal("20.00"));
        g.setPrizeAmount(new BigDecimal("18.00"));
        g.setCallInterval(3600);
        g.setWinningPattern(pattern);
        g.setStatus(GameStatus.CLAIM_PENDING);
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(g));
        return g;
    }

    private BingoClaim claim(long id, long playerId, long cardId, String cardJson) {
        BingoClaim c = BingoClaim.builder()
                .id(id)
                .gameId(GAME_ID)
                .playerId(playerId)
                .cardId(cardId)
                .cardSnapshot(cardJson)
                .result("VALID")
                .claimedAt(LocalDateTime.now().minusSeconds(30))
                .build();
        claims.add(c);
        return c;
    }

    private void calls(List<Integer> called) {
        when(calledNumberRepository.findCalledNumbersByGameId(GAME_ID)).thenReturn(called);
    }

    /** The calls that complete four full rows (0-3) on WIN_CARD: numbers 1 through 20. */
    private static List<Integer> fourLinesCalls() {
        return java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList();
    }

    @Test
    @DisplayName("auto-approve: provably complete claim is approved, paid and the game ends")
    void winIsApprovedAndSettled() {
        Game g = liveGame("FOUR_LINES");
        calls(fourLinesCalls());
        BingoClaim c = claim(1L, 101L, 1000L, WIN_CARD);

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertNotNull(c.getValidatedAt(), "the win was decided"),
                () -> assertEquals(ADMIN_ID, c.getValidatedBy()),
                () -> assertEquals(new BigDecimal("18.00"), c.getRewardAmount(),
                        "sole winner takes the whole prize"),
                () -> assertEquals(GameStatus.ENDED, g.getStatus())
        );
        verify(walletService).creditWinnings(101L, new BigDecimal("18.00"), GAME_ID);
        verify(cardService).markCardAsWinner(GAME_ID, 1000L);
    }

    @Test
    @DisplayName("auto-approve: provably incomplete claim is rejected, card banned")
    void lossIsRejectedAndCardBanned() {
        Game g = liveGame("FOUR_LINES");
        // Scattered calls: neither LOSE_CARD nor WIN_CARD completes four lines.
        calls(List.of(1, 2, 3, 7, 8, 9));
        BingoClaim c = claim(1L, 101L, 1000L, LOSE_CARD);

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertEquals("REJECTED", c.getResult()),
                () -> assertNotNull(c.getValidatedAt()),
                () -> assertTrue(c.getRejectionReason().startsWith("auto: Pattern FOUR_LINES"))
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("auto-approve: a complete pattern whose last call added no component is a loss")
    void latePatternIsRejected() {
        Game g = liveGame("FIVE_LINES");
        // Five lines (rows 0-2, columns 0-1) are complete; 25 completes no line.
        calls(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17, 21, 22, 25));
        BingoClaim c = claim(1L, 101L, 1000L, WIN_CARD);

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertEquals("REJECTED", c.getResult()),
                () -> assertNotNull(c.getValidatedAt()),
                () -> assertTrue(c.getRejectionReason().startsWith("auto: Pattern FIVE_LINES"))
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("auto-approve: two simultaneous winners both approved and the pot split")
    void simultaneousWinnersSplitThePot() {
        Game g = liveGame("FOUR_LINES");
        calls(fourLinesCalls());
        BingoClaim first = claim(1L, 101L, 1000L, WIN_CARD);
        BingoClaim second = claim(2L, 102L, 2000L, WIN_CARD);

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertNotNull(first.getValidatedAt(), "first win approved"),
                () -> assertNotNull(second.getValidatedAt(), "second win approved"),
                () -> assertEquals(new BigDecimal("9.00"), first.getRewardAmount()),
                () -> assertEquals(new BigDecimal("9.00"), second.getRewardAmount()),
                () -> assertEquals(GameStatus.ENDED, g.getStatus())
        );
        verify(walletService).creditWinnings(101L, new BigDecimal("9.00"), GAME_ID);
        verify(walletService).creditWinnings(102L, new BigDecimal("9.00"), GAME_ID);
    }

    @Test
    @DisplayName("auto-approve: a proven win next to an unreadable claim approves the win and rejects the claim")
    void winNextToUnreadableIsApprovedAndTheClaimRejected() {
        Game g = liveGame("FOUR_LINES");
        calls(fourLinesCalls());
        BingoClaim win = claim(1L, 101L, 1000L, WIN_CARD);
        BingoClaim unreadable = claim(2L, 102L, 2000L, "not-json");

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertNotNull(win.getValidatedAt(), "the provable win is approved"),
                () -> assertEquals("REJECTED", unreadable.getResult(),
                        "the unreadable claim is decided too — validation never waits for a human"),
                () -> assertNotNull(unreadable.getValidatedAt()),
                () -> assertEquals(new BigDecimal("18.00"), win.getRewardAmount(),
                        "sole confirmed winner takes the whole prize once its companion is rejected"),
                () -> assertEquals(GameStatus.ENDED, g.getStatus(),
                        "the round resolves once every claim is decided")
        );
        verify(walletService).creditWinnings(101L, new BigDecimal("18.00"), GAME_ID);
    }

    @Test
    @DisplayName("auto-approve: HALF_HOUSE wins on any one of its layouts")
    void halfHouseVariantWins() {
        Game g = liveGame("HALF_HOUSE");
        // Every non-free cell called — complete under any of the eight layouts.
        calls(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15,
                16, 17, 18, 19, 20, 21, 22, 23, 24, 25));
        BingoClaim c = claim(1L, 101L, 1000L, WIN_CARD);

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertNotNull(c.getValidatedAt(), "a complete house is a win"),
                () -> assertEquals(new BigDecimal("18.00"), c.getRewardAmount()),
                () -> assertEquals(GameStatus.ENDED, g.getStatus())
        );
    }

    @Test
    @DisplayName("auto-approve: an unreadable card is rejected — validation never waits for a human")
    void unreadableCardIsRejected() {
        Game g = liveGame("FOUR_LINES");
        calls(fourLinesCalls());
        BingoClaim c = claim(1L, 101L, 1000L, "not-json");

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertEquals("REJECTED", c.getResult(), "the claim was decided"),
                () -> assertNotNull(c.getValidatedAt()),
                () -> assertTrue(c.getRejectionReason().startsWith("auto: Pattern FOUR_LINES"))
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("auto-approve: an unknown pattern is rejected as a loss")
    void unknownPatternIsRejected() {
        Game g = liveGame("MYSTERY_PATTERN");
        calls(List.of(1, 2, 3, 4, 5, 6));
        BingoClaim c = claim(1L, 101L, 1000L, LOSE_CARD);

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertEquals("REJECTED", c.getResult(), "an unrecognized pattern cannot win"),
                () -> assertNotNull(c.getValidatedAt()),
                () -> assertTrue(c.getRejectionReason().contains("not complete"))
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("a tampered snapshot is ignored: the registered server card decides, so a real win is paid")
    void tamperedSnapshotDoesNotHideAWin() {
        Game g = liveGame("FOUR_LINES");
        calls(fourLinesCalls());
        BingoClaim c = claim(1L, 101L, 1000L, "not-json");
        // The card registered with the server completes four lines; the claim snapshot is garbage.
        when(gameCardRepository.findByGameIdAndCardId(eq(GAME_ID), eq(1000L)))
                .thenReturn(Optional.of(GameCard.builder()
                        .id(1000L)
                        .gameId(GAME_ID)
                        .card(Card.builder().id(1000L).numbers(WIN_CARD).build())
                        .build()));

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertNotNull(c.getValidatedAt()),
                () -> assertEquals(new BigDecimal("18.00"), c.getRewardAmount(),
                        "server truth pays the win, the snapshot cannot hide it"),
                () -> assertEquals(GameStatus.ENDED, g.getStatus())
        );
        verify(walletService).creditWinnings(101L, new BigDecimal("18.00"), GAME_ID);
    }

    @Test
    @DisplayName("a claim on a card the server does not hold is rejected, never left pending")
    void unregisteredCardIsRejected() {
        Game g = liveGame("FOUR_LINES");
        calls(fourLinesCalls());
        BingoClaim c = claim(1L, 101L, 1000L, WIN_CARD);
        when(gameCardRepository.findByGameIdAndCardId(eq(GAME_ID), eq(1000L))).thenReturn(Optional.empty());

        engine.automatedClaimReview(GAME_ID, ADMIN_ID);

        assertAll(
                () -> assertEquals("REJECTED", c.getResult()),
                () -> assertNotNull(c.getValidatedAt())
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("auto-approve: every canonical picker pattern approves and pays a provably complete claim")
    void everyPickerPatternApprovesACompleteClaim() {
        for (String code : pickerCodes()) {
            Game g = liveGame(code);
            claims.clear();
            calls(patternValues(requiredCells(code)));
            BingoClaim c = claim(1L, 101L, 1000L, WIN_CARD);

            engine.automatedClaimReview(GAME_ID, ADMIN_ID);

            assertAll(() -> assertNotNull(c.getValidatedAt(), code + " should decide the claim"),
                    () -> assertEquals("VALID", c.getResult(), code + " should approve a complete card"),
                    () -> assertEquals(new BigDecimal("18.00"), c.getRewardAmount(),
                            code + " sole winner takes the whole prize"),
                    () -> assertEquals(GameStatus.ENDED, g.getStatus(), code + " round should end"));
        }
    }

    @Test
    @DisplayName("auto-approve: every canonical picker pattern rejects and bans a one-cell-short claim")
    void everyPickerPatternRejectsANearMiss() {
        for (String code : pickerCodes()) {
            Game g = liveGame(code);
            claims.clear();
            calls(patternValues(withoutCell(requiredCells(code), dropFor(code))));
            BingoClaim c = claim(1L, 101L, 1000L, WIN_CARD);

            engine.automatedClaimReview(GAME_ID, ADMIN_ID);

            assertAll(() -> assertNotNull(c.getValidatedAt(), code + " should decide the near-miss"),
                    () -> assertEquals("REJECTED", c.getResult(), code + " must not approve an incomplete card"),
                    () -> assertTrue(c.getRejectionReason().startsWith("auto: Pattern " + code),
                            code + " rejection reason, got: " + c.getRejectionReason()));
        }
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    /** The canonical GamePatterns list the admin picker offers: 28 grids plus FULL_HOUSE. */
    private static List<String> pickerCodes() {
        List<String> codes = new ArrayList<>(WinningPatternGeometry.codes());
        codes.add("FULL_HOUSE");
        return codes;
    }

    /** The cells a code needs called to win; FULL_HOUSE is the whole card. */
    private static List<int[]> requiredCells(String code) {
        List<int[]> cells = SemanticDemoCells.winningCells(code);
        if (!cells.isEmpty()) {
            return cells;
        }
        List<int[]> all = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                all.add(new int[]{r, c});
            }
        }
        return all;
    }

    /** The value the test card holds at a cell (centre cell is 0, the free cell). */
    private static List<Integer> patternValues(List<int[]> cells) {
        List<Integer> called = new ArrayList<>();
        for (int[] rc : cells) {
            called.add((rc[0] == 2 && rc[1] == 2) ? 0 : rc[0] * 5 + rc[1] + 1);
        }
        return called;
    }

    private static List<int[]> withoutCell(List<int[]> cells, int[] dropped) {
        List<int[]> out = new ArrayList<>();
        for (int[] rc : cells) {
            if (rc[0] != dropped[0] || rc[1] != dropped[1]) {
                out.add(rc);
            }
        }
        return out;
    }

    /**
     * A cell whose absence must break the pattern, found by probing the semantic
     * family itself: the engine must reject the demo call-set minus that cell. Hard
     * drops are not safe anymore — dropping a bar end from a rectangle family can still
     * leave a winning layout — so the probe is what guarantees REJECTED, exactly the
     * near-miss a player must be banned for. HALF_HOUSE keeps (2,4): its other seven
     * layouts all reach rows 3-4 or the far columns, so no short call-set touches them.
     */
    private int[] dropFor(String code) {
        if ("HALF_HOUSE".equals(code)) {
            return new int[]{2, 4};
        }
        int[][] grid = winCardGrid();
        List<int[]> cells = requiredCells(code);
        for (int i = cells.size() - 1; i >= 0; i--) {
            int[] cell = cells.get(i);
            if (cell[0] == 2 && cell[1] == 2) {
                continue;
            }
            if (!engine.validateBingo(grid, patternValues(withoutCell(cells, cell)), code)) {
                return cell;
            }
        }
        throw new IllegalStateException("no single-cell drop breaks " + code);
    }

    /** The numeric card behind {@link #WIN_CARD}: value r*5+c+1 with a free centre. */
    private int[][] winCardGrid() {
        int[][] grid = new int[5][5];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                grid[r][c] = (r == 2 && c == 2) ? 0 : r * 5 + c + 1;
            }
        }
        return grid;
    }

    @Test
    @DisplayName("claim timeout: an already approved winner is paid when an undecided companion claim expires")
    void timeoutSettlesApprovedWinner() {
        Game g = liveGame("FOUR_LINES");
        calls(fourLinesCalls());
        BingoClaim approved = claim(1L, 101L, 1000L, WIN_CARD);
        approved.setValidatedAt(LocalDateTime.now().minusSeconds(10));
        approved.setValidatedBy(ADMIN_ID);
        BingoClaim stuck = claim(2L, 102L, 2000L, "not-json");
        stuck.setClaimedAt(LocalDateTime.now().minusSeconds(600));

        TenantRegistry tenant = new TenantRegistry();
        tenant.setAdminUserId(ADMIN_ID);
        when(tenantRegistryRepository.findAll()).thenReturn(List.of(tenant));
        when(gameRepository.findByStatus(GameStatus.CLAIM_PENDING)).thenReturn(List.of(g));
        when(bingoClaimRepository.findUnresolvedClaimsOnEndedGames()).thenReturn(List.of());

        engine.enforceClaimTimeout();

        assertAll(
                () -> assertEquals("REJECTED", stuck.getResult(), "the stuck claim is voided"),
                () -> assertEquals(new BigDecimal("18.00"), approved.getRewardAmount(),
                        "the proven win is paid, not stranded"),
                () -> assertEquals(GameStatus.ENDED, g.getStatus())
        );
        verify(walletService).creditWinnings(101L, new BigDecimal("18.00"), GAME_ID);
    }

    @Test
    @DisplayName("stranded VALID claim on an already-ended game is voided without a ban")
    void strandedClaimOnEndedGameIsVoided() {
        Game g = liveGame("FOUR_LINES");
        g.setStatus(GameStatus.ENDED);
        BingoClaim stranded = claim(9L, 101L, 1000L, WIN_CARD);
        stranded.setClaimedAt(LocalDateTime.now().minusSeconds(600));

        when(bingoClaimRepository.findUnresolvedClaimsOnEndedGames()).thenReturn(List.of(stranded));
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
