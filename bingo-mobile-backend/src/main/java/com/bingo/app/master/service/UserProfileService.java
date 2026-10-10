package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.master.dto.DepositAccount;
import com.bingo.app.master.dto.DepositAccountsCodec;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final DepositAccountsCodec depositAccountsCodec;

    /**
     * Builds the API profile for a user. Players keep their money in the owning
     * agent's tenant DB (players table) while the master-side users.balance stays
     * at zero, so the wallet fields are overridden with the live tenant values.
     */
    public UserProfileResponse buildProfile(User user) {
        UserProfileResponse profile = UserProfileResponse.from(user);
        if (user == null || user.getRole() != Role.PLAYER) {
            return profile.toBuilder()
                    .depositAccounts(readDepositAccounts(user))
                    .build();
        }

        Player player = TenantHelper.withTenant(user, () ->
                playerRepository.findByUserId(user.getId()).orElse(null));

        if (player == null) {
            log.warn("No player record found in tenant DB for player user {}", user.getId());
            return profile;
        }

        return profile.toBuilder()
                .balance(player.getBalance())
                .frozenBalance(player.getFrozenBalance())
                .depositAccounts(depositAccountsOfAdmin(user.getAdminUserId()))
                .build();
    }

    /** The deposit accounts a user sees: an admin's own, a player's owning admin's. */
    private List<DepositAccount> readDepositAccounts(User user) {
        if (user == null || user.getRole() == Role.PLAYER) {
            return List.of();
        }
        return depositAccountsCodec.read(user.getDepositAccountInfo());
    }

    private List<DepositAccount> depositAccountsOfAdmin(Long adminUserId) {
        if (adminUserId == null) {
            return List.of();
        }
        return userRepository.findById(adminUserId)
                .map(user -> depositAccountsCodec.read(user.getDepositAccountInfo()))
                .orElse(List.of());
    }
}
