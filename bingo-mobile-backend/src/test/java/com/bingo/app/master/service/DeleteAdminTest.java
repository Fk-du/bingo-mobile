package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantManagementService;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.exception.AdminDeletionException;
import com.bingo.app.master.repository.AdminWarningRepository;
import com.bingo.app.master.repository.CardRequestRepository;
import com.bingo.app.master.repository.InviteCodeRepository;
import com.bingo.app.master.repository.NotificationRepository;
import com.bingo.app.master.repository.OwnerFeeSettlementRepository;
import com.bingo.app.master.repository.TenantRegistryRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.dto.response.AdminGameResponse;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.PlayerService;
import com.bingo.app.tenant.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting an agent is the one super-admin action that cannot be undone, so the
 * refusals are the point of these tests: a live game and an unsettled payment both
 * stop it, and nothing is deleted when they do. The happy path is checked only to
 * prove it reaches the tenant drop and takes the players with it.
 */
class DeleteAdminTest {

    private static final Long AGENT_ID = 7L;
    private static final Long SUPER_ADMIN_ID = 1L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TenantManagementService tenantManagementService = mock(TenantManagementService.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final AdminWarningRepository adminWarningRepository = mock(AdminWarningRepository.class);
    private final CardRequestRepository cardRequestRepository = mock(CardRequestRepository.class);
    private final OwnerFeeSettlementRepository ownerFeeSettlementRepository = mock(
            OwnerFeeSettlementRepository.class);
    private final InviteCodeRepository inviteCodeRepository = mock(InviteCodeRepository.class);
    private final TenantRegistryRepository tenantRegistryRepository = mock(TenantRegistryRepository.class);
    private final GameService gameService = mock(GameService.class);
    private final WalletService walletService = mock(WalletService.class);

    private final UserService service = new UserService(
            userRepository,
            tenantManagementService,
            mock(PlayerService.class),
            mock(MasterMapper.class),
            mock(ObjectProvider.class),
            mock(NotificationService.class),
            notificationRepository,
            adminWarningRepository,
            cardRequestRepository,
            ownerFeeSettlementRepository,
            inviteCodeRepository,
            tenantRegistryRepository,
            gameService,
            walletService);

    private User agent() {
        return User.builder().id(AGENT_ID).telegramId(100L).role(Role.ADMIN)
                .businessName("Bingo Palace").active(true).adminApproved(true).build();
    }

    @Test
    @DisplayName("a running game stops the delete and nothing is removed")
    void refusesWhileAGameIsOpen() {
        when(userRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent()));
        when(gameService.findOpenGamesForAdmin(AGENT_ID))
                .thenReturn(List.of(mock(AdminGameResponse.class)));

        AdminDeletionException e = assertThrows(AdminDeletionException.class,
                () -> service.deleteAdmin(AGENT_ID, SUPER_ADMIN_ID));

        assertEquals("admin_has_open_games", e.getCode());
        verify(tenantManagementService, never()).dropTenant(any());
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("an unsettled withdrawal stops the delete")
    void refusesWhileMoneyIsPending() {
        when(userRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent()));
        when(gameService.findOpenGamesForAdmin(AGENT_ID)).thenReturn(List.of());
        when(walletService.countPendingWithdrawalsForAdmin(AGENT_ID)).thenReturn(2L);

        AdminDeletionException e = assertThrows(AdminDeletionException.class,
                () -> service.deleteAdmin(AGENT_ID, SUPER_ADMIN_ID));

        assertEquals("admin_has_pending_money", e.getCode());
        verify(tenantManagementService, never()).dropTenant(any());
    }

    @Test
    @DisplayName("a super admin cannot delete the account they are signed in with")
    void refusesSelfDeletion() {
        when(userRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent()));

        AdminDeletionException e = assertThrows(AdminDeletionException.class,
                () -> service.deleteAdmin(AGENT_ID, AGENT_ID));

        assertEquals("admin_delete_self", e.getCode());
        verify(gameService, never()).findOpenGamesForAdmin(any());
    }

    @Test
    @DisplayName("a player account is not deletable from the agent list")
    void refusesNonAdmin() {
        User player = User.builder().id(AGENT_ID).telegramId(100L).role(Role.PLAYER).build();
        when(userRepository.findById(AGENT_ID)).thenReturn(Optional.of(player));

        AdminDeletionException e = assertThrows(AdminDeletionException.class,
                () -> service.deleteAdmin(AGENT_ID, SUPER_ADMIN_ID));

        assertEquals("admin_not_an_admin", e.getCode());
    }

    @Test
    @DisplayName("a quiet agent is deleted with their tenant database and their players")
    void deletesAgentWithTenantAndPlayers() {
        User player = User.builder().id(99L).telegramId(555L).role(Role.PLAYER).adminUserId(AGENT_ID).build();
        when(userRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent()));
        when(userRepository.findAllByAdminUserId(AGENT_ID)).thenReturn(List.of(player));
        when(gameService.findOpenGamesForAdmin(AGENT_ID)).thenReturn(List.of());
        when(walletService.countPendingWithdrawalsForAdmin(AGENT_ID)).thenReturn(0L);
        when(walletService.countPendingCoinRequestsForAdmin(AGENT_ID)).thenReturn(0L);
        when(tenantManagementService.dropTenant(AGENT_ID)).thenReturn("bingo_agent_" + AGENT_ID);

        var deleted = service.deleteAdmin(AGENT_ID, SUPER_ADMIN_ID);

        assertEquals(AGENT_ID, deleted.adminUserId());
        assertEquals(1, deleted.playersRemoved());
        assertEquals("bingo_agent_" + AGENT_ID, deleted.tenantDatabase());
        verify(notificationRepository).deleteByUserIdIn(anyList());
        verify(adminWarningRepository).deleteByAdminUserId(AGENT_ID);
        verify(cardRequestRepository).deleteByAdminUserId(AGENT_ID);
        verify(ownerFeeSettlementRepository).deleteByAdminUserId(AGENT_ID);
        verify(inviteCodeRepository).deleteByCreatorId(AGENT_ID);
        verify(userRepository).deleteAll(List.of(player));
        verify(userRepository).delete(any(User.class));
    }
}