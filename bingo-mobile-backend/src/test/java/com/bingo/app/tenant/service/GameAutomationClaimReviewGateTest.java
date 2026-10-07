package com.bingo.app.tenant.service;

import com.bingo.app.master.entity.TenantRegistry;
import com.bingo.app.master.repository.TenantRegistryRepository;
import com.bingo.app.tenant.dto.AutomationConfigRequest;
import com.bingo.app.tenant.entity.AutomationConfig;
import com.bingo.app.tenant.entity.BingoClaim;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.repository.AutomationConfigRepository;
import com.bingo.app.tenant.repository.BingoClaimRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The claim-review scanner: claim validation is fully automatic and always on.
 * Every game paused in CLAIM_PENDING goes to the engine's automated review on
 * the scanner's fast cycle, no matter how the automation config is set — there
 * is no manual review and no toggle that turns it off.
 */
class GameAutomationClaimReviewGateTest {

    private static final long ADMIN = 1L;
    private static final long GAME_ID = 30L;

    private AutomationConfigRepository automationConfigRepository;
    private GameRepository gameRepository;
    private BingoClaimRepository bingoClaimRepository;
    private GameEngineService gameEngineService;
    private TransactionTemplate transactionTemplate;
    private GameAutomationService service;

    @BeforeEach
    void setUp() {
        automationConfigRepository = mock(AutomationConfigRepository.class);
        gameRepository = mock(GameRepository.class);
        bingoClaimRepository = mock(BingoClaimRepository.class);
        gameEngineService = mock(GameEngineService.class);
        TenantRegistryRepository tenantRegistryRepository = mock(TenantRegistryRepository.class);
        transactionTemplate = mock(TransactionTemplate.class);

        // Run the scanner body instead of swallowing it like Spring would.
        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> body =
                    inv.getArgument(0);
            body.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        TenantRegistry tenant = new TenantRegistry();
        tenant.setAdminUserId(ADMIN);
        when(tenantRegistryRepository.findAll()).thenReturn(List.of(tenant));

        service = new GameAutomationService(
                automationConfigRepository,
                gameRepository,
                mock(GameCardRepository.class),
                bingoClaimRepository,
                mock(GameService.class),
                gameEngineService,
                tenantRegistryRepository,
                transactionTemplate);
    }

    /** One claim-pending game with a claim old enough to pass the grace window. */
    private void stubPendingGame() {
        Game game = new Game();
        game.setId(GAME_ID);
        game.setAdminUserId(ADMIN);
        game.setStatus(GameStatus.CLAIM_PENDING);
        when(gameRepository.findAllByAdminUserIdAndStatusIn(ADMIN, List.of(GameStatus.CLAIM_PENDING)))
                .thenReturn(List.of(game));
        BingoClaim claim = BingoClaim.builder()
                .id(1L)
                .gameId(GAME_ID)
                .playerId(101L)
                .cardId(1000L)
                .result("VALID")
                .claimedAt(LocalDateTime.now().minusSeconds(30))
                .build();
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(GAME_ID, "VALID"))
                .thenReturn(List.of(claim));
    }

    @Test
    @DisplayName("a claim-pending game is auto-reviewed even with auto-approve toggled off")
    void alwaysAutoReviewsClaimPendingGames() {
        stubPendingGame();
        AutomationConfig c = AutomationConfig.builder()
                .adminUserId(ADMIN)
                .autoApprove(false)
                .enabled(false)
                .autoReview(false)
                .reviewGraceSeconds(2)
                .build();
        when(automationConfigRepository.findByAdminUserId(ADMIN)).thenReturn(Optional.of(c));

        service.runClaimReview();

        verify(gameEngineService).automatedClaimReview(GAME_ID, ADMIN);
    }

    @Test
    @DisplayName("no config row still auto-reviews claims")
    void noConfigRowAutoReviews() {
        stubPendingGame();
        when(automationConfigRepository.findByAdminUserId(ADMIN)).thenReturn(Optional.empty());

        service.runClaimReview();

        verify(gameEngineService).automatedClaimReview(GAME_ID, ADMIN);
    }

    @Test
    @DisplayName("no pending claim: the engine is not called")
    void noPendingClaimsSkipsReview() {
        stubPendingGame();
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(GAME_ID, "VALID"))
                .thenReturn(List.of());

        service.runClaimReview();

        verify(gameEngineService, never()).automatedClaimReview(anyLong(), anyLong());
    }

    @Test
    @DisplayName("a claim inside the review grace window waits for simultaneous claims")
    void graceWindowWaits() {
        Game game = new Game();
        game.setId(GAME_ID);
        game.setAdminUserId(ADMIN);
        game.setStatus(GameStatus.CLAIM_PENDING);
        when(gameRepository.findAllByAdminUserIdAndStatusIn(ADMIN, List.of(GameStatus.CLAIM_PENDING)))
                .thenReturn(List.of(game));
        BingoClaim fresh = BingoClaim.builder()
                .id(2L)
                .gameId(GAME_ID)
                .result("VALID")
                .claimedAt(LocalDateTime.now())
                .build();
        when(bingoClaimRepository.findByGameIdAndResultAndValidatedAtIsNull(GAME_ID, "VALID"))
                .thenReturn(List.of(fresh));
        when(automationConfigRepository.findByAdminUserId(ADMIN)).thenReturn(Optional.empty());

        service.runClaimReview();

        verify(gameEngineService, never()).automatedClaimReview(anyLong(), anyLong());
    }

    @Test
    @DisplayName("a fresh config saved without the field defaults autoApprove to on")
    void saveDefaultsAutoApproveOn() {
        when(automationConfigRepository.findByAdminUserId(ADMIN)).thenReturn(Optional.empty());
        when(automationConfigRepository.save(any(AutomationConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AutomationConfigRequest request = AutomationConfigRequest.builder()
                .entryFee(new java.math.BigDecimal("10.00"))
                .callInterval(5)
                .rakePercent(new java.math.BigDecimal("10.00"))
                .registrationWindowSeconds(180)
                .cooldownSeconds(15)
                .build();

        assertTrue(service.saveConfig(ADMIN, request).autoApprove(),
                "a brand-new config starts with auto-validation on");
    }

    @Test
    @DisplayName("saving without the field never flips an explicit autoApprove=false back on")
    void saveKeepsExplicitOff() {
        AutomationConfig stored = AutomationConfig.builder()
                .adminUserId(ADMIN)
                .autoApprove(false)
                .build();
        when(automationConfigRepository.findByAdminUserId(ADMIN)).thenReturn(Optional.of(stored));
        when(automationConfigRepository.save(any(AutomationConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AutomationConfigRequest request = AutomationConfigRequest.builder()
                .entryFee(new java.math.BigDecimal("10.00"))
                .callInterval(5)
                .rakePercent(new java.math.BigDecimal("10.00"))
                .registrationWindowSeconds(180)
                .cooldownSeconds(15)
                .build();

        assertFalse(service.saveConfig(ADMIN, request).autoApprove(),
                "an omitted field must not re-enable auto-validation");
    }

    @Test
    @DisplayName("getConfig without a row reports auto-approve on")
    void defaultConfigReportsAutoApproveOn() {
        when(automationConfigRepository.findByAdminUserId(ADMIN)).thenReturn(Optional.empty());

        assertTrue(service.getConfig(ADMIN).autoApprove());
    }
}