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
        @DisplayName("adds the requested number on top of what is already held")
        void previewAddsAnotherBatch() {
            holdingNothing();
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(2L);
            when(cardPreviewRepository.findByGameIdAndPlayerIdOrderByCreatedAtAsc(GAME_ID, PLAYER_ID))
                    .thenReturn(List.of(held(card(1)), held(card(2)), held(card(3)), held(card(4)),
                            held(card(5)), held(card(6)), held(card(7))));
            when(cardRepository.findRandomAvailable(any(), eq(5)))
                    .thenReturn(List.of(card(3), card(4), card(5), card(6), card(7)));

            List<PreviewCardResponse> previews = service.previewCards(GAME_ID, PLAYER_ID, 5);

            assertThat(previews).extracting(PreviewCardResponse::cardId)
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            // A fresh batch of 5 is dealt each time rather than the missing-to-a-total.
            verify(cardRepository).findRandomAvailable(any(), eq(5));
            verify(cardPreviewRepository, times(5)).save(any(CardPreview.class));
            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("deals another batch when the same count is asked again")
        void previewAddsAgain() {
            holdingNothing();
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(5L);
            when(cardPreviewRepository.findByGameIdAndPlayerIdOrderByCreatedAtAsc(GAME_ID, PLAYER_ID))
                    .thenReturn(List.of(held(card(1)), held(card(2)), held(card(3)), held(card(4)),
                            held(card(5)), held(card(6)), held(card(7)), held(card(8)), held(card(9)),
                            held(card(10))));
            when(cardRepository.findRandomAvailable(any(), eq(5)))
                    .thenReturn(List.of(card(6), card(7), card(8), card(9), card(10)));

            List<PreviewCardResponse> previews = service.previewCards(GAME_ID, PLAYER_ID, 5);

            assertThat(previews).hasSize(10);
            verify(cardRepository).findRandomAvailable(any(), eq(5));
            verify(cardPreviewRepository, times(5)).save(any(CardPreview.class));
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

        @Test
        @DisplayName("refuses a request over the 50-card limit instead of silently handing over a smaller set")
        void previewRefusesMoreThanFifty() {
            playerSees("at most 50 cards", () -> service.previewCards(GAME_ID, PLAYER_ID, 60));
            playerSees("Ask for 50 or fewer", () -> service.previewCards(GAME_ID, PLAYER_ID, 60));

            verify(cardPreviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses an additive request that would pass the 50-card limit")
        void previewAdditiveRespectsCap() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(45L);
            when(gameCardRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(0L);

            playerSees("Ask for 5 or fewer more", () -> service.previewCards(GAME_ID, PLAYER_ID, 50));

            verify(cardRepository, never()).findRandomAvailable(any(), anyInt());
            verify(cardPreviewRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reusing the previous game's cards")
    class ReusingPreviousCards {

        private GameCard dealt(long cardId, long gameId) {
            return GameCard.builder().id(cardId).gameId(gameId).playerId(PLAYER_ID).card(card(cardId)).build();
        }

        @Test
        @DisplayName("takes only the most recent game's cards, not every game the player ever played")
        void onlyThePreviousGameIsReused() {
            // The player joined game 60 last and game 50 before that.
            when(gameCardRepository.findGameIdsByPlayerExcluding(PLAYER_ID, GAME_ID))
                    .thenReturn(List.of(60L, 50L));
            when(gameCardRepository.findAllByGameIdAndPlayerId(60L, PLAYER_ID))
                    .thenReturn(List.of(dealt(1, 60), dealt(2, 60)));
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 1L))
                    .thenReturn(Optional.empty());
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 2L))
                    .thenReturn(Optional.empty());
            when(cardRepository.isCardOccupiedByOthers(eq(1L), eq(GAME_ID), eq(PLAYER_ID), any()))
                    .thenReturn(false);
            when(cardRepository.isCardOccupiedByOthers(eq(2L), eq(GAME_ID), eq(PLAYER_ID), any()))
                    .thenReturn(false);

            List<PreviewCardResponse> created = service.previewPreviousCards(GAME_ID, PLAYER_ID);

            assertThat(created).extracting(PreviewCardResponse::cardId).containsExactlyInAnyOrder(1L, 2L);
            verify(gameCardRepository, never()).findAllByGameIdAndPlayerId(50L, PLAYER_ID);
        }

        @Test
        @DisplayName("tells the player there is nothing to reuse before their first game")
        void nothingToReuseBeforeFirstGame() {
            when(gameCardRepository.findGameIdsByPlayerExcluding(PLAYER_ID, GAME_ID))
                    .thenReturn(List.of());

            playerSees("haven't played any cards yet",
                    () -> service.previewPreviousCards(GAME_ID, PLAYER_ID));

            verify(cardPreviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses the reuse when the player already holds the 50-card maximum")
        void noRoomAtTheCap() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(50L);
            when(gameCardRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(0L);

            playerSees("maximum of 50 cards",
                    () -> service.previewPreviousCards(GAME_ID, PLAYER_ID));

            verify(cardPreviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("fills only the room left when the reuse would pass the limit")
        void reuseFillsOnlyRemainingRoom() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(49L);
            when(gameCardRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(0L);
            when(gameCardRepository.findGameIdsByPlayerExcluding(PLAYER_ID, GAME_ID)).thenReturn(List.of(60L));
            when(gameCardRepository.findAllByGameIdAndPlayerId(60L, PLAYER_ID))
                    .thenReturn(List.of(dealt(1, 60), dealt(2, 60)));
            when(cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 1L))
                    .thenReturn(Optional.empty());
            when(cardRepository.isCardOccupiedByOthers(eq(1L), eq(GAME_ID), eq(PLAYER_ID), any()))
                    .thenReturn(false);

            List<PreviewCardResponse> created = service.previewPreviousCards(GAME_ID, PLAYER_ID);

            assertThat(created).extracting(PreviewCardResponse::cardId).containsExactly(1L);
            verify(cardPreviewRepository, never()).findByGameIdAndPlayerIdAndCardId(GAME_ID, PLAYER_ID, 2L);
            verify(cardPreviewRepository, times(1)).save(any(CardPreview.class));
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

        @Test
        @DisplayName("tells the player the 50-card limit once the game holds as many as allowed")
        void registerRefusesPastFifty() {
            when(gameCardRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(50L);

            playerSees("at most 50 cards", () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));
            playerSees("Remove one of your cards first", () -> service.registerPreviewedCard(GAME_ID, PLAYER_ID, 1L));

            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("registering a batch directly")
    class AutoRegistering {

        @Test
        @DisplayName("refuses a request over the 50-card limit before picking or charging for anything")
        void autoRegisterRefusesOverFifty() {
            playerSees("Ask for 50 or fewer", () -> service.assignCardsAuto(GAME_ID, PLAYER_ID, 60));

            verify(cardRepository, never()).findRandomAvailable(any(), anyInt());
            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any(GameCard.class));
        }

        @Test
        @DisplayName("refuses when the player already holds the 50-card maximum")
        void autoRegisterRefusesAtCap() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(30L);
            when(gameCardRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(20L);

            playerSees("Remove one of your cards first", () -> service.assignCardsAuto(GAME_ID, PLAYER_ID, 1));

            verify(cardRepository, never()).findRandomAvailable(any(), anyInt());
            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any(GameCard.class));
        }

        @Test
        @DisplayName("tells the player how many cards there is actually room for")
        void autoRegisterStatesRoomLeft() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(45L);
            when(gameCardRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(0L);

            playerSees("can only add 5 more", () -> service.assignCardsAuto(GAME_ID, PLAYER_ID, 10));

            verify(cardRepository, never()).findRandomAvailable(any(), anyInt());
            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any(GameCard.class));
        }

        @Test
        @DisplayName("the single-card path also refuses a player at the cap")
        void assignCardRefusesAtCap() {
            when(cardPreviewRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(50L);
            when(gameCardRepository.countByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).thenReturn(0L);
            when(gameCardRepository.findByPlayerIdAndActiveGamesExcluding(PLAYER_ID, GAME_ID))
                    .thenReturn(List.of());

            playerSees("Remove one of your cards first",
                    () -> service.assignCard(GAME_ID, PLAYER_ID, 1L));

            verify(walletService, never()).deductBet(anyLong(), any(), anyLong());
            verify(gameCardRepository, never()).save(any(GameCard.class));
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
