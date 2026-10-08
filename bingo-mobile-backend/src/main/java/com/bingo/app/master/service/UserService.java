package com.bingo.app.master.service;

import com.bingo.app.common.exception.BadRequestException;
import com.bingo.app.common.exception.ForbiddenException;
import com.bingo.app.common.exception.NotFoundException;
import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.infrastructure.persistence.TenantManagementService;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.CreateAdminRequest;
import com.bingo.app.master.dto.request.CreatePlayerRequest;
import com.bingo.app.master.dto.response.AdminDeletionResponse;
import com.bingo.app.master.dto.response.AdminListItem;
import com.bingo.app.master.dto.response.AgentStatsResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.AdminWarning;
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
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.PlayerService;
import com.bingo.app.tenant.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final TenantManagementService tenantManagementService;
    private final PlayerService playerService;
    private final MasterMapper masterMapper;
    private final ObjectProvider<InviteService> inviteServiceProvider;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final AdminWarningRepository adminWarningRepository;
    private final CardRequestRepository cardRequestRepository;
    private final OwnerFeeSettlementRepository ownerFeeSettlementRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final TenantRegistryRepository tenantRegistryRepository;
    private final GameService gameService;
    private final WalletService walletService;

    public UserService(UserRepository userRepository,
                       TenantManagementService tenantManagementService,
                       PlayerService playerService,
                       MasterMapper masterMapper,
                       ObjectProvider<InviteService> inviteServiceProvider,
                       NotificationService notificationService,
                       NotificationRepository notificationRepository,
                       AdminWarningRepository adminWarningRepository,
                       CardRequestRepository cardRequestRepository,
                       OwnerFeeSettlementRepository ownerFeeSettlementRepository,
                       InviteCodeRepository inviteCodeRepository,
                       TenantRegistryRepository tenantRegistryRepository,
                       GameService gameService,
                       WalletService walletService) {
        this.userRepository = userRepository;
        this.tenantManagementService = tenantManagementService;
        this.playerService = playerService;
        this.masterMapper = masterMapper;
        this.inviteServiceProvider = inviteServiceProvider;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.adminWarningRepository = adminWarningRepository;
        this.cardRequestRepository = cardRequestRepository;
        this.ownerFeeSettlementRepository = ownerFeeSettlementRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.tenantRegistryRepository = tenantRegistryRepository;
        this.gameService = gameService;
        this.walletService = walletService;
    }

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    @Transactional
    public User findOrCreateUser(Long telegramId, String username, String firstName, String lastName) {
        return findOrCreateUser(telegramId, username, firstName, lastName, null);
    }

    public User findOrCreateUser(Long telegramId, String username, String firstName, String lastName, String startParam) {
        User existing = userRepository.findByTelegramId(telegramId).orElse(null);
        if (existing != null) {
            return refreshTelegramProfile(existing, username, firstName, lastName);
        }

        // New user with invite code — register through InviteService (outside @Transactional to avoid poisoning)
        if (startParam != null && !startParam.isBlank()) {
            try {
                return inviteServiceProvider.getObject().registerWithInvite(telegramId, startParam);
            } catch (Exception e) {
                log.warn("Invite registration failed for code={}, falling back to default: {}", startParam, e.getMessage());
            }
        }

        return createNewUser(telegramId, username, firstName, lastName);
    }

    /**
     * Overwrites the stored Telegram profile with the live values Telegram just
     * sent. Usernames change and first/last names get edited, so we treat the
     * last seen Telegram data as authoritative — admins then see the real
     * username instead of the generic {@code user_<id>} placeholder.
     */
    public User refreshTelegramProfile(User user, String username, String firstName, String lastName) {
        if (user == null) {
            return null;
        }
        boolean changed = false;
        if (username != null && !username.equals(user.getTelegramUsername())) {
            user.setUsername(username);
            changed = true;
        }
        if (firstName != null && !firstName.isBlank() && !Objects.equals(firstName, user.getFirstName())) {
            user.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !Objects.equals(lastName, user.getLastName())) {
            user.setLastName(lastName);
            changed = true;
        }
        return changed ? userRepository.save(user) : user;
    }

    @Transactional
    public User mergeTelegramProfile(Long telegramId, String username, String firstName, String lastName) {
        User user = userRepository.findByTelegramId(telegramId).orElse(null);
        return refreshTelegramProfile(user, username, firstName, lastName);
    }

    /** Persists the phone number shared via Telegram's request_contact button. */
    @Transactional
    public User savePhoneNumber(Long telegramId, String phoneNumber) {
        User user = userRepository.findByTelegramId(telegramId).orElse(null);
        if (user == null) {
            throw new NotFoundException("User not found for telegramId=" + telegramId);
        }
        String normalized = normalizePhone(phoneNumber);
        if (normalized != null) {
            user.setPhoneNumber(normalized);
            return userRepository.save(user);
        }
        return user;
    }

    /** Sets the BCrypt password hash used for mobile-app (phone+password) login. */
    @Transactional
    public User setPassword(Long userId, String bcryptHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found for id=" + userId));
        user.setPasswordHash(bcryptHash);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findByPhoneNumber(String phoneNumber) {
        return findByPhoneNumberNormalized(normalizePhone(phoneNumber));
    }

    private User findByPhoneNumberNormalized(String normalized) {
        if (normalized == null) {
            return null;
        }
        return userRepository.findByPhoneNumber(normalized).orElse(null);
    }

    /**
     * Normalizes a phone number to international E.164 format without the '+':
     * strips spaces/dashes, accepts a leading '+' or '0'. Ethiopian numbers
     * typed locally as 09xxxxxxxxx become +2519xxxxxxxxx (i.e. 2519xxxxxxxxx).
     */
    public static String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0")) {
            digits = "251" + digits.substring(1);
        }
        return digits;
    }

    private User createNewUser(Long telegramId, String username, String firstName, String lastName) {
        Role role = telegramId.equals(superAdminTelegramId) ? Role.SUPER_ADMIN : Role.PLAYER;

        User user = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .active(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User ensureSuperAdmin(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User superAdmin = User.builder()
                            .telegramId(telegramId)
                            .username("superadmin")
                            .firstName("Super")
                            .lastName("Admin")
                            .role(Role.SUPER_ADMIN)
                            .active(true)
                            .build();
                    return userRepository.save(superAdmin);
                });
    }

    /**
     * Creates a super-admin row for phone+password (mobile) deployments. Reuses
     * the configured Telegram super-admin if it exists (so web + mobile share one
     * row); otherwise creates a placeholder with a synthetic unique telegramId.
     */
    @Transactional
    public User createSuperAdminForMobile(String phone) {
        // Reuse the configured Telegram super admin, if any, so both apps map to
        // the same users row.
        if (superAdminTelegramId != null) {
            User existing = userRepository.findByTelegramId(superAdminTelegramId).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Long placeholderId = syntheticTelegramId();
        return User.builder()
                .telegramId(placeholderId)
                .username("superadmin")
                .firstName("Super")
                .lastName("Admin")
                .role(Role.SUPER_ADMIN)
                .active(true)
                .build();
    }

    /** Negative telegramIds are reserved for non-Telegram bootstrapped accounts. */
    private Long syntheticTelegramId() {
        long id = -2L;
        while (userRepository.existsByTelegramId(id)) {
            id--;
        }
        return id;
    }

    // NOT @Transactional — createTenant() runs CREATE DATABASE which cannot be in a transaction.
    public User createAdmin(CreateAdminRequest request) {
        User admin = User.builder()
                .telegramId(request.telegramId())
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.ADMIN)
                .parentId(request.creatorId())
                .adminApproved(false)
                .active(true)
                .build();

        User saved = userRepository.save(admin);
        tenantManagementService.createTenant(saved.getId());
        return saved;
    }

    @Transactional
    public AdminListItem approveAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("User is not an admin: " + adminUserId);
        }
        admin.setAdminApproved(true);
        admin.setActive(true);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        notificationService.notify(admin.getId(), "ADMIN_APPROVED",
                "Account approved",
                "Your admin account has been approved. You can now start managing your bingo room.",
                "notify.admin.approved", null,
                null, null,
                "\u2705 Account approved\nYou are now an active admin. Open the app to get started!");

        return result;
    }

    @Transactional
    public AdminListItem rejectAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("User is not an admin: " + adminUserId);
        }
        admin.setAdminApproved(false);
        admin.setActive(false);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        notificationService.notify(admin.getId(), "ADMIN_REJECTED",
                "Account rejected",
                "Your admin account request was rejected. Contact the platform owner for details.",
                "notify.admin.rejected", null,
                null, null, null);

        return result;
    }

    @Transactional
    public AdminListItem suspendAdmin(Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        admin.setActive(false);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        // Freeze the agent's operation: halt any open games in their tenant so
        // their players can no longer join, call, or claim. Players are separate
        // User rows (role PLAYER) and are not blocked by the admin's own 403, so
        // we must end the open games explicitly to stop them.
        String tenantId = TenantContext.tenantKeyForAdmin(adminUserId);
        TenantContext.setTenant(tenantId);
        try {
            List<Long> openGameIds = gameService.findOpenGamesForAdmin(adminUserId).stream()
                    .map(AdminGameResponse::id)
                    .toList();
            for (Long gameId : openGameIds) {
                try {
                    gameService.endGameManually(gameId, adminUserId);
                    log.info("Suspended admin {} -> ended game {}", adminUserId, gameId);
                } catch (RuntimeException e) {
                    log.warn("Failed to end game {} on admin suspension: {}", gameId, e.getMessage());
                }
            }
        } finally {
            TenantContext.clear();
        }

        notificationService.notify(admin.getId(), "ADMIN_SUSPENDED",
                "Account suspended",
                "Your admin account has been suspended by the platform. All your active games were ended. Contact the owner for details.",
                "notify.admin.suspended", null,
                null, null,
                "⏸️ Account suspended\nYour admin account was suspended and your active games were ended. Contact the platform owner.");

        // Notify the agent's players that their room is frozen.
        List<User> players = userRepository.findAllByAdminUserId(adminUserId);
        for (User player : players) {
            notificationService.notify(player.getId(), "ADMIN_SUSPENDED",
                    "Agent suspended",
                    "Your agent account has been suspended. All games are frozen until further notice.",
                    "notify.player.agentSuspended", null,
                    null, null,
                    "⏸️ Your agent account was suspended. Games are frozen until further notice.");
        }

        return result;
    }

    @Transactional
    public AdminListItem resumeAdmin(Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        admin.setActive(true);
        AdminListItem result = masterMapper.toAdminListItem(userRepository.save(admin));

        notificationService.notify(admin.getId(), "ADMIN_RESUMED",
                "Account resumed",
                "Your admin account has been resumed. You can continue managing your bingo room.",
                "notify.admin.resumed", null,
                null, null,
                "▶️ Account resumed\nYour admin account is active again.");

        return result;
    }

    @Transactional
    public AdminWarning warnAdmin(Long adminUserId, String reason, Long createdBy) {
        User admin = requireAdmin(adminUserId);
        AdminWarning warning = AdminWarning.builder()
                .adminUserId(adminUserId)
                .reason(reason)
                .createdBy(createdBy)
                .build();
        AdminWarning saved = adminWarningRepository.save(warning);

        notificationService.notify(admin.getId(), "ADMIN_WARNING",
                "Warning from platform",
                "You have received a warning: " + reason,
                "notify.admin.warning", "{\"reason\":\"" + reason.replace("\"", "") + "\"}",
                null, null,
                "⚠️ Warning\n" + reason);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<AdminWarning> getWarningsForAdmin(Long adminUserId) {
        return adminWarningRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId);
    }

    /**
     * Delete an agent for good: their tenant database (games, cards, wallets,
     * transactions), the players registered under them, their invite links, and
     * every master row that points at them.
     *
     * <p>Refused while a game is still open or money is still in flight. Deleting
     * is irreversible, and a live game with a pot in it — or a pending withdrawal
     * — is not something a stray tap may resolve. Suspending the agent first is the
     * deliberate route: that ends the open games and leaves the account there to be
     * deleted once the money is settled.
     *
     * <p>NOT {@code @Transactional}, for the same reason {@link #createAdmin} is
     * not: {@code dropTenant()} runs {@code DROP DATABASE}, which PostgreSQL refuses
     * inside a transaction block. The steps go children-first and the agent row
     * last, so a failure part-way leaves the agent still listed and the delete
     * retryable rather than a gone agent with its history still attached.
     */
    public AdminDeletionResponse deleteAdmin(Long adminUserId, Long actingUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> AdminDeletionException.notFound(adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw AdminDeletionException.notAnAdmin(adminUserId);
        }
        if (adminUserId.equals(actingUserId)) {
            throw AdminDeletionException.cannotDeleteSelf();
        }
        requireNothingOutstanding(adminUserId);

        List<User> players = userRepository.findAllByAdminUserId(adminUserId);
        List<Long> userIds = new ArrayList<>();
        userIds.add(adminUserId);
        players.forEach(p -> userIds.add(p.getId()));

        // Children first: the master rows only reference the agent by id, so this
        // is about not leaving history pointing at an account that no longer exists.
        notificationRepository.deleteByUserIdIn(userIds);
        adminWarningRepository.deleteByAdminUserId(adminUserId);
        cardRequestRepository.deleteByAdminUserId(adminUserId);
        ownerFeeSettlementRepository.deleteByAdminUserId(adminUserId);
        inviteCodeRepository.deleteByCreatorId(adminUserId);

        String tenantDatabase = tenantManagementService.dropTenant(adminUserId);

        if (!players.isEmpty()) {
            userRepository.deleteAll(players);
        }
        userRepository.delete(admin);

        log.warn("Super admin {} deleted agent {} ({}), {} player(s), tenant {}",
                actingUserId, adminUserId, admin.getBusinessName(), players.size(), tenantDatabase);
        return new AdminDeletionResponse(
                adminUserId, admin.getBusinessName(), players.size(), tenantDatabase);
    }

    /**
     * The two states that make a delete unsafe: a game with players in it, and
     * money the agent has not paid out yet. Both live in the tenant schema, so
     * they are read with that tenant in context.
     */
    private void requireNothingOutstanding(Long adminUserId) {
        TenantContext.setTenant(TenantContext.tenantKeyForAdmin(adminUserId));
        try {
            int openGames = gameService.findOpenGamesForAdmin(adminUserId).size();
            if (openGames > 0) {
                throw AdminDeletionException.hasOpenGames(openGames);
            }
            long withdrawals = walletService.countPendingWithdrawalsForAdmin(adminUserId);
            long coinRequests = walletService.countPendingCoinRequestsForAdmin(adminUserId);
            if (withdrawals > 0 || coinRequests > 0) {
                throw AdminDeletionException.hasPendingMoney(withdrawals, coinRequests);
            }
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Platform-level stats for a single agent, aggregated from that agent's tenant schema.
     */
    public AgentStatsResponse getAgentStats(Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        String tenantId = TenantContext.tenantKeyForAdmin(adminUserId);
        TenantContext.setTenant(tenantId);
        try {
            long totalGames = gameService.getAllGamesForAdmin(adminUserId).size();
            long endedGames = gameService.getAllGamesForAdmin(adminUserId).stream()
                    .filter(g -> g.status() == GameStatus.ENDED)
                    .count();
            long totalPlayers = playerService.getPlayersByAdmin(adminUserId).size();
            long totalTransactions = walletService.getAllTransactions().size();
            BigDecimal totalCommission = walletService.getTotalCommissionInTenant();
            return new AgentStatsResponse(
                    totalGames, endedGames, totalPlayers, totalTransactions,
                    totalCommission, admin.getBalance());
        } finally {
            TenantContext.clear();
        }
    }

    private User requireAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("User is not an admin: " + adminUserId);
        }
        return admin;
    }

    public User createPlayer(CreatePlayerRequest request) {
        User player = User.builder()
                .telegramId(request.telegramId())
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.PLAYER)
                .adminUserId(request.adminUserId())
                .parentId(request.parentId())
                .active(true)
                .build();

        User saved = userRepository.save(player);

        String tenant = TenantContext.tenantKeyForAdmin(request.adminUserId());
        TenantContext.setTenant(tenant);
        try {
            playerService.createPlayer(saved.getId(), request.adminUserId(), request.parentId());
            log.info("Player record created in tenant DB for user: {}", saved.getId());
        } finally {
            TenantContext.clear();
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public User findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getPlayersByAdmin(Long adminUserId) {
        return userRepository.findAllByAdminUserId(adminUserId).stream()
                .map(masterMapper::toUserProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return masterMapper.toUserProfile(user);
    }

    @Transactional(readOnly = true)
    public User findByIdEntity(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Transactional
    public void deductBalance(Long userId, BigDecimal amount) {
        int updated = userRepository.deductBalance(userId, amount);
        if (updated == 0) {
            throw new BadRequestException("Insufficient balance");
        }
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByRole(Role role) {
        return userRepository.findAllByRole(role).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByParentIdAndRole(Long parentId, Role role) {
        return userRepository.findAllByParentIdAndRole(parentId, role).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByAdminUserId(Long adminUserId) {
        return userRepository.findAllByAdminUserId(adminUserId).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }
}
