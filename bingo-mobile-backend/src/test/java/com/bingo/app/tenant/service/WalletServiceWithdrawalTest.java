package com.bingo.app.tenant.service;

import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.master.service.NotificationService;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.WithdrawalResponse;
import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.entity.Withdrawal;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.tenant.repository.CoinRequestRepository;
import com.bingo.app.tenant.repository.PlayerRepository;
import com.bingo.app.tenant.repository.TransactionRepository;
import com.bingo.app.tenant.repository.WithdrawalRepository;
import com.bingo.app.master.repository.OwnerFeeSettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The withdrawal floor: cashing out must leave at least the minimum in the wallet
 * (minWithdrawal, 100 coins), while gameplay spending down to zero stays untouched.
 * Only the refusals and the freeze are checked here — the payout flow lives elsewhere.
 */
class WalletServiceWithdrawalTest {

    private static final long PLAYER_ID = 42L;
    private static final BigDecimal MIN_WITHDRAWAL = new BigDecimal("100");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CoinRequestRepository coinRequestRepository = mock(CoinRequestRepository.class);
    private final WithdrawalRepository withdrawalRepository = mock(WithdrawalRepository.class);
    private final OwnerFeeSettlementRepository ownerFeeSettlementRepository =
            mock(OwnerFeeSettlementRepository.class);
    private final TenantMapper tenantMapper = mock(TenantMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ConfigService configService = mock(ConfigService.class);

    private final WalletService service = new WalletService(
            userRepository, playerRepository, playerService, transactionRepository,
            coinRequestRepository, withdrawalRepository, ownerFeeSettlementRepository,
            tenantMapper, notificationService, configService);

    private void givenPlayerWithBalance(String balance) {
        Player player = Player.builder()
                .userId(PLAYER_ID)
                .adminUserId(null)
                .balance(new BigDecimal(balance))
                .build();
        when(playerRepository.findByUserId(PLAYER_ID)).thenReturn(Optional.of(player));
        when(configService.getMinWithdrawal()).thenReturn(MIN_WITHDRAWAL);
    }

    @Test
    @DisplayName("An amount below the minimum is refused and nothing is frozen")
    void rejectsAmountBelowMinimum() {
        givenPlayerWithBalance("500");

        WalletException e = assertThrows(WalletException.class,
                () -> service.createWithdrawRequest(PLAYER_ID, new BigDecimal("50"), "CBE 1000"));

        assertTrue(e.getUserMessage().contains("Minimum withdrawal is 100"), e.getUserMessage());
        verify(playerService, never()).freezeBalance(any(), any());
        verify(withdrawalRepository, never()).save(any());
    }

    @Test
    @DisplayName("A withdrawal that would leave the wallet below the minimum is refused")
    void rejectsWithdrawalDroppingWalletBelowMinimum() {
        givenPlayerWithBalance("150");

        WalletException e = assertThrows(WalletException.class,
                () -> service.createWithdrawRequest(PLAYER_ID, new BigDecimal("100"), "CBE 1000"));

        assertTrue(e.getUserMessage().contains("drop below 100"), e.getUserMessage());
        assertTrue(e.getUserMessage().contains("up to 50"), e.getUserMessage());
        verify(playerService, never()).freezeBalance(any(), any());
        verify(withdrawalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Emptying the wallet through a withdrawal is refused")
    void rejectsFullBalanceWithdrawal() {
        givenPlayerWithBalance("150");

        WalletException e = assertThrows(WalletException.class,
                () -> service.createWithdrawRequest(PLAYER_ID, new BigDecimal("150"), "CBE 1000"));

        assertTrue(e.getUserMessage().contains("drop below 100"), e.getUserMessage());
        verify(playerService, never()).freezeBalance(any(), any());
    }

    @Test
    @DisplayName("Requesting more than the balance is still refused first")
    void rejectsAmountAboveBalance() {
        givenPlayerWithBalance("50");

        WalletException e = assertThrows(WalletException.class,
                () -> service.createWithdrawRequest(PLAYER_ID, new BigDecimal("100"), "CBE 1000"));

        assertTrue(e.getUserMessage().contains("Your balance is 50"), e.getUserMessage());
        verify(playerService, never()).freezeBalance(any(), any());
    }

    @Test
    @DisplayName("Withdrawing so the wallet keeps exactly the minimum is allowed")
    void allowsWithdrawalLeavingExactlyTheMinimum() {
        givenPlayerWithBalance("200");
        when(withdrawalRepository.save(any(Withdrawal.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tenantMapper.toDto(any(Withdrawal.class))).thenReturn(
                WithdrawalResponse.builder().userId(PLAYER_ID).amount(new BigDecimal("100")).build());

        WithdrawalResponse response = service.createWithdrawRequest(
                PLAYER_ID, new BigDecimal("100"), "CBE 1000");

        assertEquals(new BigDecimal("100"), response.amount());
        verify(playerService).freezeBalance(PLAYER_ID, new BigDecimal("100"));
        verify(withdrawalRepository).save(any(Withdrawal.class));
    }
}
