package com.bingo.app.tenant.service;

import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.GameCardResponse;
import com.bingo.app.tenant.dto.response.PreviewCardResponse;
import com.bingo.app.tenant.entity.Card;
import com.bingo.app.tenant.entity.CardPreview;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.entity.GameCard;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.exception.PlayerActionException;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.tenant.repository.CardPreviewRepository;
import com.bingo.app.tenant.repository.CardRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Preview-then-register: a player sees the cards they would get before paying for
 * any of them, registers the ones they want one at a time, and can drop a card
 * from their board -- refunding a paid card and simply releasing a preview.
 *
 * <p>The rule these tests defend: money only ever moves on
 * {@code registerPreviewedCard}, and the pot only ever holds money from cards that
 * are actually in play.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardPreviewRegistrationTest {

    private static final Long GAME_ID = 70L;
    private static final Long PLAYER_ID = 5L;
    private static final BigDecimal FEE = new BigDecimal("10.00");

    @Mock CardRepository cardRepository;
    @Mock GameCardRepository gameCardRepository;
    @Mock GameRepository gameRepository;
    @Mock CardPreviewRepository cardPreviewRepository;
    @Mock PlayerService playerService;
    @Mock WalletService walletService;
    @Mock TenantMapper tenantMapper;

    private CardService service;
    private Game game;

    @BeforeEach
    void setUp() {
        service = new CardService(
                cardRepository, gameCardRepository, gameRepository, cardPreviewRepository,
                playerService, walletService, new ObjectMapper(), tenantMapper);
        game = Game.builder()
                .id(GAME_ID)
                .status(GameStatus.REGISTRATION_OPEN)
                .entryFee(FEE)
                .prizePool(BigDecimal.ZERO)
                .build();
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(playerService.getBalance(PLAYER_ID)).thenReturn(new BigDecimal("100.00"));
        when(gameCardRepository.save(any(GameCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantMapper.toDto(any(GameCard.class))).thenAnswer(inv ->
                GameCardResponse.builder().id(1L).gameId(GAME_ID).playerId(PLAYER_ID).build());
        when(cardPreviewRepository.save(any(CardPreview.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Assert on the message the player is shown rather than the internal one. The
     * detail that matters here (the shortfall, how many cards are left) lives in
     * {@code userMessage} and is what the app renders.
     */
    private static void playerSees(String text, ThrowingCallable call) {
        assertThatThrownBy(call)
                .as("player-facing message")
                .satisfies(t -> assertThat(userMessageOf(t)).contains(text));
    }

    private static String userMessageOf(Throwable t) {
        if (t instanceof PlayerActionException e) return e.getUserMessage();
        if (t instanceof WalletException e) return e.getUserMessage();
        return t.getMessage();
    }

    private Card card(long id) {
        return Card.builder().id(id).numbers("[[1,2],[3,4]]").usageCount(0).build();
    }

    private CardPreview held(Card card) {
        return CardPreview.builder().id(100L).gameId(GAME_ID).playerId(PLAYER_ID).card(card).build();
    }

    /** Nothing previewed, nothing registered anywhere. */
    private void holdingNothing() {
        when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(0L);
        when(cardPreviewRepository.findByGameIdAndPlayerIdOrderByCreatedAtAsc(GAME_ID, PLAYER_ID))
                .thenReturn(List.of());
    }

    @Nested
    @DisplayName("previewing")
    class Previewing {

        @Test
        @DisplayName("holds the requested cards and charges nothing")
        void previewIsFree() {
            holdingNothing();
            when(cardRepository.findRandomAvailable(any(), eq(5)))
                    .thenReturn(List.of(card(1), card(2), card(3), card(4), card(5)));

            service.previewCards(GAME_ID, PLAYER_ID, 5);

            verify(cardPreviewRepository, times(5)).save(any(CardPreview.class));
            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            assertThat(game.getPrizePool()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("tops the holding up to the requested count instead of dealing a second batch")
        void previewTopsUp() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(2L);
            when(cardPreviewRepository.findByGameIdAndPlayerIdOrderByCreatedAtAsc(GAME_ID, PLAYER_ID))
                    .thenReturn(List.of(held(card(1)), held(card(2))));
            when(cardRepository.findRandomAvailable(any(), eq(3)))
                    .thenReturn(List.of(card(3), card(4), card(5)));

            List<PreviewCardResponse> previews = service.previewCards(GAME_ID, PLAYER_ID, 5);

            assertThat(previews).extracting(PreviewCardResponse::cardId).containsExactly(1L, 2L);
            // Only the 3 missing cards are picked up; the 2 already held are not replaced.
            verify(cardRepository).findRandomAvailable(any(), eq(3));
            verify(cardPreviewRepository, times(3)).save(any(CardPreview.class));
            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("re-requesting what is already held does not deal anything new")
        void previewIsIdempotent() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(5L);
            when(cardPreviewRepository.findByGameIdAndPlayerIdOrderByCreatedAtAsc(GAME_ID, PLAYER_ID))
                    .thenReturn(List.of(held(card(1)), held(card(2)), held(card(3)), held(card(4)), held(card(5))));

            List<PreviewCardResponse> previews = service.previewCards(GAME_ID, PLAYER_ID, 5);

            assertThat(previews).hasSize(5);
            verify(cardRepository, never()).findRandomAvailable(any(), anyInt());
        }

        @Test
        @DisplayName("says how many cards are actually left instead of quietly showing fewer")
        void previewReportsShortfall() {
            holdingNothing();
            when(cardRepository.findRandomAvailable(any(), eq(5))).thenReturn(List.of(card(1), card(2)));

            playerSees("Only 2 cards are still available",
                    () -> service.previewCards(GAME_ID, PLAYER_ID, 5));

            verify(cardPreviewRepository, never()).save(any());
            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("is refused once the game stops accepting registrations")
        void previewNeedsRegistrationOpen() {
            game.setStatus(GameStatus.IN_PROGRESS);

            playerSees("no longer accepting registrations",
                    () -> service.previewCards(GAME_ID, PLAYER_ID, 5));

            verify(cardPreviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("is refused for a player already registered in another live game")
        void previewRespectsOneActiveGame() {
            holdingNothing();
            when(gameCardRepository.findByPlayerIdAndActiveGamesExcluding(PLAYER_ID, GAME_ID))
                    .thenReturn(List.of(GameCard.builder().id(9L).gameId(8L).playerId(PLAYER_ID).build()));

            playerSees("already registered for an active game",
                    () -> service.previewCards(GAME_ID, PLAYER_ID, 5));
        }
    }

    @Nested
    @DisplayName("registering one previewed card")
    class Registering {

        @BeforeEach
        void holdingOneCard() {
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 1L))
                    .thenReturn(Optional.of(held(card(1))));
            when(cardRepository.isCardOccupiedByOthers(eq(1L), eq(GAME_ID), eq(PLAYER_ID), any()))
                    .thenReturn(false);
        }

        @Test
        @DisplayName("charges one entry fee, adds it to the pot and deals the card")
        void registerChargesOnce() {
            service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L);

            verify(walletService).deductBet(PLAYER_ID, FEE, GAME_ID);
            verify(cardPreviewRepository).delete(any(CardPreview.class));
            verify(gameCardRepository).save(any(GameCard.class));
            assertThat(game.getPrizePool()).isEqualByComparingTo(FEE);
        }

        @Test
        @DisplayName("does not mistake the player's own hold for somebody else taking the card")
        void registerAllowsOwnHold() {
            service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L);

            verify(cardRepository).isCardOccupiedByOthers(eq(1L), eq(GAME_ID), eq(PLAYER_ID), any());
            verify(cardRepository, never()).isCardOccupied(eq(1L), any());
        }

        @Test
        @DisplayName("refuses a card another player got to first")
        void registerRefusesStolenCard() {
            when(cardRepository.isCardOccupiedByOthers(eq(1L), eq(GAME_ID), eq(PLAYER_ID), any()))
                    .thenReturn(true);

            playerSees("just taken by another player",
                    () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));

            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("tells the player exactly how many more coins are needed, and charges nothing")
        void registerReportsShortfall() {
            when(playerService.getBalance(PLAYER_ID)).thenReturn(new BigDecimal("4.00"));

            playerSees("need 6 more coins", () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));
            playerSees("Your balance is 4 coins", () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));
            playerSees("this card costs 10 coins", () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));

            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any());
            assertThat(game.getPrizePool()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("refuses a card the player is not holding")
        void registerRefusesUnheldCard() {
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 9L))
                    .thenReturn(Optional.empty());

            playerSees("not one of the cards you are holding",
                    () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 9L));

            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("is refused once the game leaves registration")
        void registerNeedsRegistrationOpen() {
            game.setStatus(GameStatus.IN_PROGRESS);

            playerSees("no longer accepting registrations",
                    () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));

            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("cannot be used to join a second live game while holding a preview in this one")
        void registerRespectsOneActiveGame() {
            when(gameCardRepository.findByPlayerIdAndActiveGamesExcluding(PLAYER_ID, GAME_ID))
                    .thenReturn(List.of(GameCard.builder().id(9L).gameId(8L).playerId(PLAYER_ID).build()));

            playerSees("already registered for an active game",
                    () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));

            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removing a card with X")
    class Removing {

        @Test
        @DisplayName("releases an unpaid preview without touching money")
        void removePreviewRefundsNothing() {
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 1L))
                    .thenReturn(Optional.of(held(card(1))));

            CardService.UnregisterResult result = service.unregisterCard(GAME_ID, PLAYER_ID, 1L);

            assertThat(result.wasRegistered()).isFalse();
            assertThat(result.refund()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(cardPreviewRepository).delete(any(CardPreview.class));
            verify(walletService, never()).refundPlayer(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).delete(any());
        }

        @Test
        @DisplayName("refunds a registered card and takes the fee back out of the pot")
        void removeRegisteredRefundsAndShrinksPot() {
            game.setPrizePool(FEE.multiply(BigDecimal.valueOf(3)));
            GameCard registered = registeredCard(1L);
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 1L))
                    .thenReturn(Optional.empty());
            when(gameCardRepository.findAllByGameIdAndPlayerId(GAME_ID, PLAYER_ID))
                    .thenReturn(List.of(registered));

            CardService.UnregisterResult result = service.unregisterCard(GAME_ID, PLAYER_ID, 1L);

            assertThat(result.wasRegistered()).isTrue();
            assertThat(result.refund()).isEqualByComparingTo(FEE);
            verify(walletService).refundPlayer(PLAYER_ID, FEE, GAME_ID);
            verify(gameCardRepository).delete(registered);
            assertThat(game.getPrizePool()).isEqualByComparingTo(FEE.multiply(BigDecimal.valueOf(2)));
        }

        @Test
        @DisplayName("never drives the pot negative")
        void removeRegisteredFloorsPotAtZero() {
            game.setPrizePool(new BigDecimal("5.00"));
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 1L))
                    .thenReturn(Optional.empty());
            when(gameCardRepository.findAllByGameIdAndPlayerId(GAME_ID, PLAYER_ID))
                    .thenReturn(List.of(registeredCard(1L)));

            service.unregisterCard(GAME_ID, PLAYER_ID, 1L);

            assertThat(game.getPrizePool()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("refuses a card the player does not have")
        void removeRefusesForeignCard() {
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 4L))
                    .thenReturn(Optional.empty());
            when(gameCardRepository.findAllByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(List.of());

            playerSees("You do not hold this card", () -> service.unregisterCard(GAME_ID, PLAYER_ID, 4L));

            verify(walletService, never()).refundPlayer(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("is refused once the game leaves registration, so a paid card cannot be pulled mid-game")
        void removeNeedsRegistrationOpen() {
            game.setStatus(GameStatus.IN_PROGRESS);

            playerSees("no longer accepting registrations",
                    () -> service.unregisterCard(GAME_ID, PLAYER_ID, 1L));

            verify(walletService, never()).refundPlayer(anyLong(), any(), anyLong());
        }
    }

    private GameCard registeredCard(long cardId) {
        return GameCard.builder()
                .id(9L).gameId(GAME_ID).playerId(PLAYER_ID).card(card(cardId)).winner(false).build();
    }
}
