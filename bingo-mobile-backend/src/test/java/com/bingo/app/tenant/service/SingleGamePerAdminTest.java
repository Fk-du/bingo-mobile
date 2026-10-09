package com.bingo.app.tenant.service;

import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.repository.AutomationConfigRepository;
import com.bingo.app.tenant.repository.BingoClaimRepository;
import com.bingo.app.tenant.repository.CardPreviewRepository;
import com.bingo.app.tenant.repository.CalledNumberRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.bingo.app.tenant.repository.TransactionRepository;
import com.bingo.app.master.service.NotificationService;
import com.bingo.app.tenant.dto.CreateGameRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * An admin runs a single table at a time. These tests use a mocked repository so they can
 * check the guard itself, including the case the old query missed: a game still collecting
 * registrations must also block a second one.
 */
class SingleGamePerAdminTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameService service = new GameService(
            gameRepository,
            mock(CalledNumberRepository.class),
            mock(BingoClaimRepository.class),
            mock(GameCardRepository.class),
            mock(TransactionRepository.class),
            mock(PlayerService.class),
            mock(com.bingo.app.tenant.dto.mapper.TenantMapper.class),
            mock(PrizeRules.class),
            mock(AutomationConfigRepository.class),
            mock(CardPreviewRepository.class),
            mock(NotificationService.class)
    );

    private CreateGameRequest request() {
        return CreateGameRequest.builder()
                .entryFee(new BigDecimal("10.00"))
                .winningPattern("FULL_HOUSE")
                .callInterval(5)
                .build();
    }

    @Test
    @DisplayName("a second game is refused while one is still live")
    void refusesSecondGameWhileOneIsLive() {
        when(gameRepository.hasLiveGame(1L)).thenReturn(true);

        GameProgressException e = assertThrows(GameProgressException.class,
                () -> service.createGameWithEntryFee(1L, request()));

        // The technical message names the condition; getUserMessage is what the player sees.
        assertTrue(e.getMessage().toLowerCase().contains("live game"));
        assertTrue(e.getUserMessage().toLowerCase().contains("end it"));
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    @DisplayName("the guard counts a game that is still in registration")
    void countsRegistrationAsLive() {
        // hasLiveGame includes REGISTRATION_OPEN, unlike the old hasActiveGame.
        when(gameRepository.hasLiveGame(1L)).thenReturn(true);
        assertThrows(GameProgressException.class, () -> service.createGameWithEntryFee(1L, request()));
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    @DisplayName("a new game is created once nothing is live")
    void createsWhenNothingIsLive() {
        when(gameRepository.hasLiveGame(1L)).thenReturn(false);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        // The mapper is mocked, so toDto() returns null; the creation itself is what matters.
        assertDoesNotThrow(() -> service.createGameWithEntryFee(1L, request()));
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    @DisplayName("the guard is a separate query from the old active-game check, and covers registration")
    void guardCoversEveryLiveStatus() {
        // The old hasActiveGame skipped REGISTRATION_OPEN, which is exactly how a second
        // open table slipped through. The replacement names all five live statuses.
        when(gameRepository.hasLiveGame(1L)).thenReturn(true);
        assertThrows(GameProgressException.class, () -> service.createGameWithEntryFee(1L, request()));
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    @DisplayName("a brand new game starts in registration")
    void newGameIsRegistrationOpen() {
        when(gameRepository.hasLiveGame(1L)).thenReturn(false);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createGameWithEntryFee(1L, request());

        var captor = org.mockito.ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        assertEquals(GameStatus.REGISTRATION_OPEN, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("only the canonical 29 patterns can be chosen")
    void rejectsPatternsOutsideTheCanonicalList() {
        when(gameRepository.hasLiveGame(1L)).thenReturn(false);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(GameProgressException.class, () -> service.createGameWithEntryFee(1L,
                CreateGameRequest.builder().entryFee(new BigDecimal("10.00")).winningPattern("CUSTOM").build()));
        assertThrows(GameProgressException.class, () -> service.createGameWithEntryFee(1L,
                CreateGameRequest.builder().entryFee(new BigDecimal("10.00")).winningPattern("BLACKOUT").build()));
    }

    @Test
    @DisplayName("an omitted pattern falls back to the first canonical entry")
    void defaultsToFullHouse() {
        when(gameRepository.hasLiveGame(1L)).thenReturn(false);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createGameWithEntryFee(1L,
                CreateGameRequest.builder().entryFee(new BigDecimal("10.00")).build());

        var captor = org.mockito.ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        assertEquals("FULL_HOUSE", captor.getValue().getWinningPattern());
    }
}
