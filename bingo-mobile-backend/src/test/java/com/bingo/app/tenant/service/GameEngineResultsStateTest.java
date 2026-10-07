package com.bingo.app.tenant.service;

import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.master.service.NotificationService;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.GameStateResponse;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The game-state results board: once a game is over, the player and the admin
 * both see every winning and every banned card of the round (not just the
 * caller's own cards), so the results address the whole room.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameEngineResultsStateTest {

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

    private static final long GAME_ID = 30L;
    private static final String CARD_A = "[[1,2,3,4,5],[6,7,8,9,10],[11,12,0,14,15],[16,17,18,19,20],[21,22,23,24,25]]";
    private static final String CARD_B = "[[2,3,4,5,6],[7,8,9,10,11],[12,13,0,15,16],[17,18,19,20,21],[22,23,24,25,26]]";
    private static final String CARD_C = "[[3,4,5,6,7],[8,9,10,11,12],[13,14,0,16,17],[18,19,20,21,22],[23,24,25,26,27]]";

    @BeforeEach
    void setUp() {
        engine = new GameEngineService(gameRepository, calledNumberRepository, gameCardRepository,
                cardPreviewRepository, bingoClaimRepository, walletService, cardService, objectMapper,
                realTransactionTemplate(), messagingTemplate, tenantMapper,
                null, userRepository, notificationService, configService, gameService,
                new PrizeRules(configService));
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

    private Card card(long id, String numbers) {
        return Card.builder().id(id).numbers(numbers).build();
    }

    private GameCard gameCard(long id, long cardId, long playerId, String numbers, boolean winner, boolean banned) {
        return GameCard.builder()
                .id(id)
                .gameId(GAME_ID)
                .playerId(playerId)
                .card(card(cardId, numbers))
                .winner(winner)
                .banned(banned)
                .build();
    }

    private Game endedGame() {
        Game g = new Game();
        g.setId(GAME_ID);
        g.setAdminUserId(2L);
        g.setStatus(GameStatus.ENDED);
        g.setPrizeAmount(new BigDecimal("18.00"));
        g.setPrizePool(new BigDecimal("20.00"));
        g.setAutoMark(true);
        g.setWinningPattern("FOUR_LINES");
        g.setCurrentCallIndex(20);
        g.setTotalNumbersCalled(20);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(g));
        return g;
    }

    @Test
    @DisplayName("player state lists every winning and every banned card of the round")
    void playerStateIncludesWholeGameResults() {
        endedGame();
        when(calledNumberRepository.findCalledNumbersByGameId(GAME_ID)).thenReturn(List.of(1, 2, 3, 4, 5));
        // The caller holds card A (a winner); cards B (winner) and C (banned) belong to others.
        when(gameCardRepository.findAllByGameIdAndPlayerId(GAME_ID, 101L))
                .thenReturn(List.of(gameCard(11L, 1000L, 101L, CARD_A, true, false)));
        when(gameCardRepository.findByGameIdAndWinnerTrue(GAME_ID))
                .thenReturn(List.of(gameCard(11L, 1000L, 101L, CARD_A, true, false),
                        gameCard(12L, 2000L, 102L, CARD_B, true, false)));
        when(gameCardRepository.findByGameId(GAME_ID))
                .thenReturn(List.of(gameCard(11L, 1000L, 101L, CARD_A, true, false),
                        gameCard(12L, 2000L, 102L, CARD_B, true, false),
                        gameCard(13L, 3000L, 103L, CARD_C, false, true)));
        when(bingoClaimRepository.findByGameIdAndResult(GAME_ID, "VALID"))
                .thenReturn(List.of(
                        BingoClaim.builder().gameId(GAME_ID).cardId(1000L).result("VALID")
                                .rewardAmount(new BigDecimal("9.00")).build(),
                        BingoClaim.builder().gameId(GAME_ID).cardId(2000L).result("VALID")
                                .rewardAmount(new BigDecimal("9.00")).build()));
        when(bingoClaimRepository.findByGameIdAndPlayerIdAndResult(GAME_ID, 101L, "VALID"))
                .thenReturn(Optional.of(BingoClaim.builder().gameId(GAME_ID).cardId(1000L)
                        .result("VALID").rewardAmount(new BigDecimal("9.00")).build()));
        when(bingoClaimRepository.countByGameIdAndResultAndValidatedAtIsNotNull(GAME_ID, "VALID"))
                .thenReturn(2L);

        GameEngineService.GameState state = engine.getGameState(GAME_ID, 101L);

        assertAll(
                () -> assertTrue(state.isWinner()),
                () -> assertEquals(new BigDecimal("9.00"), state.getRewardAmount()),
                () -> assertEquals(2, state.getWinnerCount()),
                () -> assertEquals(2, state.getWinnerCards().size(), "both winners, not just the caller's"),
                () -> assertEquals(2000L, state.getWinnerCards().get(1).cardId()),
                () -> assertEquals(new BigDecimal("9.00"), state.getWinnerCards().get(0).rewardAmount()),
                () -> assertEquals(1, state.getBannedCards().size()),
                () -> assertEquals(3000L, state.getBannedCards().get(0).cardId())
        );
    }

    @Test
    @DisplayName("player state sends no results lists while the game is still live")
    void playerStateEmptyResultsBeforeEnd() {
        Game g = endedGame();
        g.setStatus(GameStatus.IN_PROGRESS);
        when(calledNumberRepository.findCalledNumbersByGameId(GAME_ID)).thenReturn(List.of(1, 2, 3));
        when(gameCardRepository.findAllByGameIdAndPlayerId(GAME_ID, 101L))
                .thenReturn(List.of(gameCard(11L, 1000L, 101L, CARD_A, false, false)));

        GameEngineService.GameState state = engine.getGameState(GAME_ID, 101L);

        assertAll(
                () -> assertTrue(state.getWinnerCards().isEmpty()),
                () -> assertTrue(state.getBannedCards().isEmpty())
        );
        verify(gameCardRepository, never()).findByGameIdAndWinnerTrue(GAME_ID);
        verify(gameCardRepository, never()).findByGameId(GAME_ID);
    }

    @Test
    @DisplayName("admin state lists every winning and every banned card of the round")
    void adminStateIncludesWholeGameResults() {
        endedGame();
        when(calledNumberRepository.findCalledNumbersByGameId(GAME_ID)).thenReturn(List.of(1, 2, 3, 4, 5));
        when(gameCardRepository.countDistinctPlayersByGameId(GAME_ID)).thenReturn(3L);
        when(gameCardRepository.findByGameIdAndWinnerTrue(GAME_ID))
                .thenReturn(List.of(gameCard(11L, 1000L, 101L, CARD_A, true, false),
                        gameCard(12L, 2000L, 102L, CARD_B, true, false)));
        when(gameCardRepository.findByGameId(GAME_ID))
                .thenReturn(List.of(gameCard(11L, 1000L, 101L, CARD_A, true, false),
                        gameCard(12L, 2000L, 102L, CARD_B, true, false),
                        gameCard(13L, 3000L, 103L, CARD_C, false, true)));
        when(bingoClaimRepository.findByGameIdAndResult(GAME_ID, "VALID"))
                .thenReturn(List.of(
                        BingoClaim.builder().gameId(GAME_ID).cardId(1000L).result("VALID")
                                .rewardAmount(new BigDecimal("9.00")).build(),
                        BingoClaim.builder().gameId(GAME_ID).cardId(2000L).result("VALID")
                                .rewardAmount(new BigDecimal("9.00")).build()));

        GameEngineService.AdminGameState state = engine.getAdminGameState(GAME_ID);

        assertAll(
                () -> assertEquals(2, state.getWinnerCards().size()),
                () -> assertEquals(2000L, state.getWinnerCards().get(1).cardId()),
                () -> assertEquals(1, state.getBannedCards().size()),
                () -> assertEquals(3000L, state.getBannedCards().get(0).cardId())
        );
    }
}