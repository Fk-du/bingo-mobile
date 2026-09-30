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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for claim-time screening (bingoClaimRules): the admin always decides who
 * wins, but a claim whose card does not contain the game's last called number can
 * never be a real Bingo, so it is rejected the moment it is submitted and only
 * the card that tried to claim is banned. Every other claim waits for the admin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameEngineClaimScreeningTest {

    @Mock GameRepository gameRepository;
    @Mock CalledNumberRepository calledNumberRepository;
    @Mock GameCardRepository gameCardRepository;
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

    private static final long GAME_ID = 30L;
    private static final long PLAYER_ID = 101L;
    private static final long CARD_ID = 1000L;
    private static final long OTHER_CARD_ID = 1001L;

    /** Contains 1..25 (0 is the free centre square). */
    private static final String CARD_WITH_5 =
            "[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]";
    /** Has no 5 anywhere. */
    private static final String CARD_WITHOUT_5 =
            "[[1,2,3,4,6],[7,8,9,10,11],[12,13,0,15,16],[17,18,19,20,21],[22,23,24,25,26]]";

    @BeforeEach
    void setUp() {
        when(configService.getOwnerFeePercent()).thenReturn(java.math.BigDecimal.ZERO);
        engine = new GameEngineService(gameRepository, calledNumberRepository, gameCardRepository,
                bingoClaimRepository, walletService, cardService, objectMapper,
                realTransactionTemplate(), messagingTemplate, tenantMapper,
                null, userRepository, notificationService, configService, gameService,
                new PrizeRules(configService));
        when(configService.getMinPrizePercent()).thenReturn(new java.math.BigDecimal("50"));
        when(configService.getMaxPrizePercent()).thenReturn(new java.math.BigDecimal("90"));
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bingoClaimRepository.save(any(BingoClaim.class))).thenAnswer(inv -> inv.getArgument(0));
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

    private Game liveGame() {
        Game g = new Game();
        g.setId(GAME_ID);
        g.setAdminUserId(2L);
        g.setPrizePool(new java.math.BigDecimal("20.00"));
        g.setPrizeAmount(new java.math.BigDecimal("18.00"));
        g.setCallInterval(3600);
        g.setStatus(GameStatus.IN_PROGRESS);
        return g;
    }

    private GameCard heldCard(long cardId, String numbersJson) {
        return GameCard.builder()
                .id(cardId)
                .gameId(GAME_ID)
                .playerId(PLAYER_ID)
                .card(Card.builder().id(cardId).numbers(numbersJson).build())
                .banned(false)
                .build();
    }

    private void stubClaimContext(Game game, GameCard card, List<Integer> called) {
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(game));
        when(gameCardRepository.findByGameIdAndCardId(GAME_ID, card.getCard().getId()))
                .thenReturn(Optional.of(card));
        when(calledNumberRepository.findCalledNumbersByGameId(GAME_ID)).thenReturn(called);
        when(bingoClaimRepository.existsByGameIdAndCardIdAndResult(GAME_ID, card.getCard().getId(), "VALID"))
                .thenReturn(false);
        when(bingoClaimRepository.existsByGameIdAndPlayerIdAndResult(GAME_ID, PLAYER_ID, "VALID"))
                .thenReturn(false);
    }

    @Test
    @DisplayName("card without the last called number: rejected on submit, card banned, game never pauses")
    void claimWithoutLastCalledNumberIsRejectedAndBanned() throws Exception {
        Game game = liveGame();
        GameCard card = heldCard(CARD_ID, CARD_WITHOUT_5);
        stubClaimContext(game, card, List.of(1, 2, 3, 4, 5));

        var result = engine.claimBingo(GAME_ID, PLAYER_ID, CARD_ID, null, null);

        assertAll(
                () -> assertFalse(result.isValid(), "an impossible claim is not valid"),
                () -> assertTrue(result.isBanned(), "the player is told the card was banned"),
                () -> assertFalse(result.isPendingReview(), "no admin review is queued"),
                () -> assertTrue(card.isBanned(), "the claimed card is frozen"),
                () -> assertEquals(GameStatus.IN_PROGRESS, game.getStatus(),
                        "an impossible claim never pauses the game")
        );

        var saved = org.mockito.ArgumentCaptor.forClass(BingoClaim.class);
        verify(bingoClaimRepository).save(saved.capture());
        assertAll(
                () -> assertEquals("REJECTED", saved.getValue().getResult()),
                () -> assertTrue(saved.getValue().getRejectionReason().startsWith("auto: ")),
                () -> assertTrue(saved.getValue().getRejectionReason().contains("last called number")),
                () -> assertNotNull(saved.getValue().getValidatedAt(), "the claim is closed, not left open")
        );

        verify(notificationService).notify(eq(PLAYER_ID), eq("CARD_BANNED"), anyString(),
                contains("last called number"), anyString(), anyString(), eq("GAME"), eq(GAME_ID), anyString());
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("only the card that tried to claim is frozen; the player's other cards keep playing")
    void onlyTheClaimingCardIsBanned() throws Exception {
        Game game = liveGame();
        GameCard cheatingCard = heldCard(CARD_ID, CARD_WITHOUT_5);
        GameCard otherCard = heldCard(OTHER_CARD_ID, CARD_WITH_5);
        stubClaimContext(game, cheatingCard, List.of(1, 2, 3, 4, 5));

        engine.claimBingo(GAME_ID, PLAYER_ID, CARD_ID, null, null);

        assertAll(
                () -> assertTrue(cheatingCard.isBanned()),
                () -> assertFalse(otherCard.isBanned(), "the other card is untouched")
        );
        verify(gameCardRepository, never()).save(same(otherCard));
    }

    @Test
    @DisplayName("card with the last called number: left pending for the admin, game pauses")
    void claimWithLastCalledNumberIsLeftForAdmin() throws Exception {
        Game game = liveGame();
        GameCard card = heldCard(CARD_ID, CARD_WITH_5);
        stubClaimContext(game, card, List.of(1, 2, 3, 4, 5));

        var result = engine.claimBingo(GAME_ID, PLAYER_ID, CARD_ID, null, null);

        assertAll(
                () -> assertTrue(result.isValid()),
                () -> assertTrue(result.isPendingReview(), "the admin decides winners"),
                () -> assertFalse(result.isBanned()),
                () -> assertFalse(card.isBanned(), "a possible claim is never banned"),
                () -> assertEquals(GameStatus.CLAIM_PENDING, game.getStatus())
        );

        var saved = org.mockito.ArgumentCaptor.forClass(BingoClaim.class);
        verify(bingoClaimRepository).save(saved.capture());
        assertAll(
                () -> assertEquals("VALID", saved.getValue().getResult()),
                () -> assertNull(saved.getValue().getRejectionReason()),
                () -> assertNull(saved.getValue().getValidatedAt(), "still waiting for a human")
        );
        verify(walletService, never()).creditWinnings(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("no number called yet: nothing to check, claim waits for the admin")
    void claimBeforeAnyNumberIsCalledIsLeftForAdmin() throws Exception {
        Game game = liveGame();
        GameCard card = heldCard(CARD_ID, CARD_WITHOUT_5);
        stubClaimContext(game, card, List.of());

        var result = engine.claimBingo(GAME_ID, PLAYER_ID, CARD_ID, null, null);

        assertAll(
                () -> assertTrue(result.isPendingReview()),
                () -> assertFalse(result.isBanned(), "with no last number there is nothing to prove cheating"),
                () -> assertFalse(card.isBanned())
        );
    }

    @Test
    @DisplayName("unreadable card data proves nothing: claim waits for the admin instead of a ban")
    void unreadableCardIsLeftForAdminRatherThanBanned() throws Exception {
        Game game = liveGame();
        GameCard card = heldCard(CARD_ID, "not-json");
        stubClaimContext(game, card, List.of(1, 2, 3, 4, 5));

        var result = engine.claimBingo(GAME_ID, PLAYER_ID, CARD_ID, null, null);

        assertAll(
                () -> assertTrue(result.isPendingReview()),
                () -> assertFalse(result.isBanned(), "bad server data must not ban a player"),
                () -> assertFalse(card.isBanned())
        );
    }

    @Test
    @DisplayName("a banned card cannot claim again")
    void bannedCardCannotClaimAgain() {
        Game game = liveGame();
        GameCard banned = heldCard(CARD_ID, CARD_WITH_5);
        banned.setBanned(true);
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(game));
        when(gameCardRepository.findByGameIdAndCardId(GAME_ID, CARD_ID)).thenReturn(Optional.of(banned));

        var ex = assertThrows(com.bingo.app.tenant.exception.GameProgressException.class,
                () -> engine.claimBingo(GAME_ID, PLAYER_ID, CARD_ID, null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("banned"));
    }
}
