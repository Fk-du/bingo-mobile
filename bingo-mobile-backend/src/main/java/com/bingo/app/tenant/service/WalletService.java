package com.bingo.app.tenant.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.tenant.exception.RequestAlreadyProcessedException;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.master.entity.OwnerFeeSettlement;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.FundStatus;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.OwnerFeeSettlementRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.dto.response.OwnerFeeSummaryResponse;
import com.bingo.app.master.dto.response.AdminOwnerFeeSummaryResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.CoinRequestResponse;
import com.bingo.app.tenant.dto.response.TransactionResponse;
import com.bingo.app.tenant.dto.response.WithdrawalResponse;
import com.bingo.app.tenant.entity.CoinRequest;
import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.entity.Transaction;
import com.bingo.app.tenant.entity.Withdrawal;
import com.bingo.app.tenant.enums.RequestStatus;
import com.bingo.app.tenant.enums.TransactionStatus;
import com.bingo.app.tenant.enums.TransactionType;
import com.bingo.app.tenant.repository.CoinRequestRepository;
import com.bingo.app.tenant.repository.PlayerRepository;
import com.bingo.app.tenant.repository.TransactionRepository;
import com.bingo.app.tenant.repository.WithdrawalRepository;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.master.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final TransactionRepository transactionRepository;
    private final CoinRequestRepository coinRequestRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final OwnerFeeSettlementRepository ownerFeeSettlementRepository;
    private final TenantMapper tenantMapper;
    private final NotificationService notificationService;
    private final ConfigService configService;

    // =========================================================
    // PLAYER METHODS
    // =========================================================

    @Transactional(transactionManager = "tenantTransactionManager")
    public CoinRequestResponse buyPoints(Long playerId, BigDecimal amount, String screenshotUrl) {
        Player player = playerRepository.findByUserId(playerId)
                .orElseThrow(() -> new WalletException("Player not found"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WalletException("Amount must be positive");
        }

        if (screenshotUrl == null || screenshotUrl.isBlank()) {
            notifyMissingPaymentScreenshot(playerId, amount);
            throw new WalletException("Payment screenshot is required",
                    "Attach your payment screenshot before submitting the deposit request.");
        }

        CoinRequest request = CoinRequest.builder()
                .userId(playerId)
                .amount(amount)
                .screenshotUrl(screenshotUrl)
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        CoinRequest saved = coinRequestRepository.save(request);

        createTransaction(playerId, TransactionType.TOP_UP, amount,
                TransactionStatus.PENDING, saved.getId(), "Points purchase requested");

        notifyDepositRequested(player, saved);

        log.info("Buy points request created for player {}: amount={}", playerId, amount);
        return tenantMapper.toDto(saved);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public WithdrawalResponse createWithdrawRequest(Long playerId, BigDecimal amount, String payoutDetails) {
        Player player = playerRepository.findByUserId(playerId)
                .orElseThrow(() -> new WalletException("Player not found"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WalletException("Amount must be positive");
        }

        BigDecimal minWithdrawal = configService.getMinWithdrawal();
        if (amount.compareTo(minWithdrawal) < 0) {
            notifyMinWithdrawalViolation(playerId, amount, minWithdrawal);
            throw new WalletException("Withdrawal amount below minimum",
                    "Minimum withdrawal is " + minWithdrawal.stripTrailingZeros().toPlainString() + " coins. You requested " + amount.stripTrailingZeros().toPlainString() + ".");
        }

        if (player.getBalance().compareTo(amount) < 0) {
            throw new WalletException("Insufficient balance",
                    "Your balance is " + player.getBalance().toPlainString() + " coins. You requested " + amount.toPlainString() + ".");
        }

        playerService.freezeBalance(playerId, amount);

        Withdrawal withdrawal = Withdrawal.builder()
                .userId(playerId)
                .amount(amount)
                .payoutMethod("BANK_TRANSFER")
                .payoutDetails(payoutDetails)
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Withdrawal saved = withdrawalRepository.save(withdrawal);

        createTransaction(playerId, TransactionType.WITHDRAWAL, amount,
                TransactionStatus.PENDING, saved.getId(), "Withdrawal request created");

        notifyWithdrawalRequested(player, saved);

        log.info("Withdrawal request created for player {}: amount={}", playerId, amount);
        return tenantMapper.toDto(saved);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<TransactionResponse> getHistory(Long playerId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(playerId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public BigDecimal getBalance(Long playerId) {
        return playerService.getBalance(playerId);
    }

    // =========================================================
    // ADMIN METHODS
    // =========================================================

    @Transactional(transactionManager = "tenantTransactionManager")
    public void fundPlayer(Long adminUserId, Long playerId, BigDecimal amount) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new WalletException("Admin not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new WalletException("Only admins can fund players");
        }

        Player player = playerRepository.findByUserId(playerId)
                .orElseThrow(() -> new WalletException("Player not found"));

        if (!player.getAdminUserId().equals(adminUserId)) {
            throw new WalletException("Player does not belong to this admin");
        }

        // Admins fund players freely (no credit ceiling); the platform owner is
        // compensated via the per-game commission accrual, not credit sales.
        playerService.addBalance(playerId, amount);

        createTransaction(adminUserId, TransactionType.FUND_AGENT_TO_PLAYER, amount,
                TransactionStatus.COMPLETED, null, "Funded player " + playerId);
        createTransaction(playerId, TransactionType.DEPOSIT, amount,
                TransactionStatus.COMPLETED, null, "Received from admin " + adminUserId);

        notifyPlayerFunded(playerId, adminUserId, amount);

        log.info("Admin {} funded player {} with amount {}", adminUserId, playerId, amount);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<WithdrawalResponse> getPendingWithdrawsForAdminPlayers(Long adminUserId) {
        List<Player> players = playerRepository.findByAdminUserId(adminUserId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();

        return withdrawalRepository.findByUserIdInOrderByCreatedAtDesc(playerIds).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<WithdrawalResponse> getPlayerWithdrawals(Long playerId) {
        return withdrawalRepository.findByUserIdOrderByCreatedAtDesc(playerId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void approveWithdrawal(Long withdrawalId, Long approverId) {
        // Atomic claim: a concurrent/double approval can never proceed past this line.
        if (withdrawalRepository.claimForProcessing(withdrawalId, RequestStatus.APPROVED,
                RequestStatus.PENDING, approverId, LocalDateTime.now(), null) == 0) {
            throw new RequestAlreadyProcessedException("Withdrawal already processed");
        }

        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new WalletException("Withdrawal not found"));

        playerService.unfreezeBalance(withdrawal.getUserId(), withdrawal.getAmount());

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        withdrawalId, TransactionType.WITHDRAWAL.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(transaction);
        }

        notifyWithdrawalApproved(withdrawal);

        log.info("Withdrawal {} approved by {}", withdrawalId, approverId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void rejectWithdrawal(Long withdrawalId, Long approverId, String reason) {
        // Atomic claim: a concurrent/double rejection can never proceed past this line.
        if (withdrawalRepository.claimForProcessing(withdrawalId, RequestStatus.REJECTED,
                RequestStatus.PENDING, approverId, LocalDateTime.now(), reason) == 0) {
            throw new RequestAlreadyProcessedException("Withdrawal already processed");
        }

        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new WalletException("Withdrawal not found"));

        playerService.returnFrozenBalance(withdrawal.getUserId(), withdrawal.getAmount());

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        withdrawalId, TransactionType.WITHDRAWAL.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }

        notifyWithdrawalRejected(withdrawal, reason);

        log.info("Withdrawal {} rejected by {}: {}", withdrawalId, approverId, reason);
    }

    // =========================================================
    // SUPER ADMIN METHODS
    // =========================================================

    @Transactional(transactionManager = "tenantTransactionManager")
    public void approveCoinRequest(Long requestId, Long approverId) {
        CoinRequest request = coinRequestRepository.findById(requestId)
                .orElseThrow(() -> new WalletException("Coin request not found"));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RequestAlreadyProcessedException("Request already processed");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new WalletException("Approver not found"));

        if (approver.getRole() != Role.ADMIN) {
            throw new WalletException("Only admins can approve coin requests");
        }

        // Atomic claim: a concurrent/double approval can never proceed past this line.
        if (coinRequestRepository.claimForProcessing(requestId, RequestStatus.APPROVED,
                RequestStatus.PENDING, approverId, LocalDateTime.now(), null) == 0) {
            throw new RequestAlreadyProcessedException("Request already processed");
        }

        // Tenant DB work first: if any of it fails, the master-side deduction below
        // must not have happened yet (the two DBs cannot share one transaction).
        playerService.addBalance(request.getUserId(), request.getAmount());

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        requestId, TransactionType.TOP_UP.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setDescription("Approved by " + approverId);
            transactionRepository.save(transaction);
        }

        createTransaction(approverId, TransactionType.FUND_AGENT_TO_PLAYER, request.getAmount(),
                TransactionStatus.COMPLETED, requestId, "Funded player " + request.getUserId());

        notifyDepositApproved(request);

        log.info("Coin request {} approved by {} — deducted from approver balance", requestId, approverId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void rejectCoinRequest(Long requestId, Long approverId, String reason) {
        // Atomic claim: a concurrent/double rejection can never proceed past this line.
        if (coinRequestRepository.claimForProcessing(requestId, RequestStatus.REJECTED,
                RequestStatus.PENDING, approverId, LocalDateTime.now(), reason) == 0) {
            throw new RequestAlreadyProcessedException("Request already processed");
        }

        Transaction transaction = transactionRepository.findByReferenceIdAndType(
                        requestId, TransactionType.TOP_UP.name())
                .orElse(null);

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }

        CoinRequest request = coinRequestRepository.findById(requestId).orElse(null);
        notifyDepositRejected(request, reason);

        log.info("Coin request {} rejected by {}: {}", requestId, approverId, reason);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<CoinRequestResponse> getPendingCoinRequestsForAdmin(Long adminUserId) {
        List<Player> players = playerRepository.findByAdminUserId(adminUserId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();

        return coinRequestRepository.findByUserIdInAndStatus(playerIds, RequestStatus.PENDING).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public long countPendingCoinRequestsForAdmin(Long adminUserId) {
        List<Player> players = playerRepository.findByAdminUserId(adminUserId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();
        if (playerIds.isEmpty()) return 0L;
        return coinRequestRepository.countByUserIdInAndStatus(playerIds, RequestStatus.PENDING);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public long countPendingWithdrawalsForAdmin(Long adminUserId) {
        List<Player> players = playerRepository.findByAdminUserId(adminUserId);
        List<Long> playerIds = players.stream().map(Player::getUserId).toList();
        if (playerIds.isEmpty()) return 0L;
        return withdrawalRepository.countByUserIdInAndStatus(playerIds, RequestStatus.PENDING);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<CoinRequestResponse> getCoinRequestsByUser(Long userId) {
        return coinRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    // =========================================================
    // GAME RELATED METHODS
    // =========================================================

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deductBet(Long playerId, BigDecimal amount, Long gameId) {
        playerService.deductBalance(playerId, amount);

        createTransaction(playerId, TransactionType.BET, amount,
                TransactionStatus.COMPLETED, gameId, "Bet placed for game " + gameId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void creditWinnings(Long playerId, BigDecimal amount, Long gameId) {
        playerService.addBalance(playerId, amount);

        createTransaction(playerId, TransactionType.WIN, amount,
                TransactionStatus.COMPLETED, gameId, "Won from game " + gameId);

        notifyGameWin(playerId, amount, gameId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void refundPlayer(Long playerId, BigDecimal amount, Long gameId) {
        playerService.addBalance(playerId, amount);

        createTransaction(playerId, TransactionType.REFUND, amount,
                TransactionStatus.COMPLETED, gameId, "Entry fee refund for ended game " + gameId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void creditAgentCommission(Long adminUserId, BigDecimal amount, Long gameId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new WalletException("Admin not found"));

        admin.setBalance(admin.getBalance().add(amount));
        userRepository.save(admin);

        createTransaction(adminUserId, TransactionType.AGENT_COMMISSION, amount,
                TransactionStatus.COMPLETED, gameId, "Agent commission from game " + gameId);

        notifyCommissionCredited(adminUserId, amount, gameId);
    }

    /**
     * Accrues the owner's per-game commission share into the tenant ledger as a
     * PLATFORM_FEE row (no wallet credit — payment happens offline in cash).
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void accrueOwnerFee(BigDecimal amount, Long gameId) {
        // Charge against an arbitrary (but deterministic) userId; the ledger is just
        // an accounting row scoped to the tenant.  We use the super admin id so the
        // row is consistently attributable to the owner.
        Long ownerId = userRepository.findFirstByRole(Role.SUPER_ADMIN)
                .map(User::getId)
                .orElse(0L);
        createTransaction(ownerId, TransactionType.PLATFORM_FEE, amount,
                TransactionStatus.COMPLETED, gameId, "Owner share from game " + gameId);
    }

    // =========================================================
    // OWNER FEE SETTLEMENTS
    // =========================================================

    @Transactional(transactionManager = "tenantTransactionManager")
    public OwnerFeeSettlement createOwnerFeeSettlement(Long adminUserId, BigDecimal amount, String screenshotUrl) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new WalletException("Admin not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new WalletException("Only admins can submit owner fee settlements");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WalletException("Amount must be positive");
        }

        OwnerFeeSettlement settlement = OwnerFeeSettlement.builder()
                .adminUserId(adminUserId)
                .amount(amount)
                .screenshotUrl(screenshotUrl)
                .status(FundStatus.PENDING)
                .build();

        OwnerFeeSettlement saved = ownerFeeSettlementRepository.save(settlement);
        notifyOwnerFeeSettlementCreated(saved);
        log.info("Admin {} submitted owner fee settlement of {}", adminUserId, amount);
        return saved;
    }

    /** Atomic settle-approval: no wallet credit needed — cash was already exchanged offline. */
    public void approveOwnerFeeSettlement(Long settlementId, Long superAdminId) {
        User superAdmin = userRepository.findById(superAdminId)
                .orElseThrow(() -> new WalletException("Super admin not found"));
        if (superAdmin.getRole() != Role.SUPER_ADMIN) {
            throw new WalletException("Only super admin can approve owner fee settlements");
        }

        OwnerFeeSettlement settlement = ownerFeeSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new WalletException("Settlement not found"));
        if (settlement.getStatus() != FundStatus.PENDING) {
            throw new RequestAlreadyProcessedException("Settlement already processed");
        }

        if (ownerFeeSettlementRepository.claimForProcessing(settlementId,
                FundStatus.APPROVED, FundStatus.PENDING,
                superAdminId, LocalDateTime.now(), null) == 0) {
            throw new RequestAlreadyProcessedException("Settlement already processed");
        }

        notifyOwnerFeeSettlementApproved(settlement);
        log.info("Owner fee settlement {} approved by super admin {}", settlementId, superAdminId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void rejectOwnerFeeSettlement(Long settlementId, Long superAdminId, String reason) {
        if (ownerFeeSettlementRepository.claimForProcessing(settlementId,
                FundStatus.REJECTED, FundStatus.PENDING,
                superAdminId, LocalDateTime.now(), reason) == 0) {
            throw new RequestAlreadyProcessedException("Settlement already processed");
        }
        var rejected = ownerFeeSettlementRepository.findById(settlementId).orElse(null);
        if (rejected != null) {
            notifyOwnerFeeSettlementRejected(rejected);
        }
        log.info("Owner fee settlement {} rejected by super admin {}", settlementId, superAdminId);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<OwnerFeeSettlement> getPendingOwnerFeeSettlements() {
        return ownerFeeSettlementRepository.findByStatusOrderByCreatedAtDesc(FundStatus.PENDING);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<OwnerFeeSettlement> getAllOwnerFeeSettlements() {
        return ownerFeeSettlementRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<OwnerFeeSettlement> getOwnerFeeSettlementsByAdmin(Long adminUserId) {
        return ownerFeeSettlementRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public OwnerFeeSummaryResponse getOwnerFeeSummaryForAdmin(Long adminUserId) {
        BigDecimal accrued = getTotalPlatformFeeInTenant();
        BigDecimal settled = ownerFeeSettlementRepository.sumApprovedByAdmin(adminUserId);
        return OwnerFeeSummaryResponse.builder()
                .accrued(accrued)
                .settled(settled)
                .owed(accrued.subtract(settled))
                .build();
    }

    /**
     * Owner fee status for every admin, so the super admin can see who has paid
     * and who still owes. Accrued is scoped to each admin's own tenant DB.
     */
    public List<AdminOwnerFeeSummaryResponse> getOwnerFeeSummaryForAllAdmins() {
        List<AdminOwnerFeeSummaryResponse> result = new ArrayList<>();
        for (User admin : userRepository.findAllByRole(Role.ADMIN)) {
            TenantContext.setTenant(TenantContext.tenantKeyForAdmin(admin.getId()));
            try {
                BigDecimal accrued = getTotalPlatformFeeInTenant();
                BigDecimal settled = ownerFeeSettlementRepository.sumApprovedByAdmin(admin.getId());
                LocalDateTime lastSettledAt = ownerFeeSettlementRepository
                        .findTopByAdminUserIdAndStatusOrderByApprovedAtDesc(admin.getId(), FundStatus.APPROVED)
                        .map(OwnerFeeSettlement::getApprovedAt)
                        .orElse(null);
                result.add(AdminOwnerFeeSummaryResponse.builder()
                        .adminUserId(admin.getId())
                        .businessName(admin.getBusinessName())
                        .username(admin.getTelegramUsername())
                        .accrued(accrued)
                        .settled(settled)
                        .owed(accrued.subtract(settled))
                        .lastSettledAt(lastSettledAt)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to compute owner fee summary for admin {}: {}", admin.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        return result;
    }

    // Owner fee settlement notifications (mirror of fund-request flow) --------------------------

    private void notifyOwnerFeeSettlementCreated(OwnerFeeSettlement s) {
        String adminName = playerDisplayName(s.getAdminUserId());
        for (User superAdmin : userRepository.findAllByRole(Role.SUPER_ADMIN)) {
            notify(superAdmin.getId(), "OWNER_FEE_SETTLEMENT",
                    "Owner fee settlement submitted",
                    adminName + " settled " + fmt(s.getAmount()) + " in owner fees.",
                    "OWNER_FEE_SETTLEMENT", s.getId(), null);
        }
    }

    private void notifyOwnerFeeSettlementApproved(OwnerFeeSettlement s) {
        notify(s.getAdminUserId(), "OWNER_FEE_SETTLEMENT_APPROVED",
                "Fee settlement approved",
                "Your owner fee settlement of " + fmt(s.getAmount()) + " was approved.",
                "OWNER_FEE_SETTLEMENT", s.getId(),
                "\u2705 Owner fee settlement approved\n" + fmt(s.getAmount()) + " has been marked as paid.");
    }

    private void notifyOwnerFeeSettlementRejected(OwnerFeeSettlement s) {
        String why = (s.getRejectionReason() == null || s.getRejectionReason().isBlank())
                ? "Please contact support." : s.getRejectionReason();
        notify(s.getAdminUserId(), "OWNER_FEE_SETTLEMENT_REJECTED",
                "Fee settlement rejected",
                "Your owner fee settlement of " + fmt(s.getAmount()) + " was rejected. Reason: " + why,
                null, null,
                "\u274C Owner fee settlement rejected\n" + fmt(s.getAmount()) + "\nReason: " + why);
    }

    // =========================================================
    // PRIVATE HELPER METHODS
    // =========================================================

    private void createTransaction(Long userId, TransactionType type, BigDecimal amount,
                                   TransactionStatus status, Long referenceId, String description) {
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .type(type.name())
                .amount(amount)
                .status(status)
                .referenceId(referenceId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
    }

    // =========================================================
    // NOTIFICATION HELPERS
    // =========================================================

    private String fmt(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String playerDisplayName(Long userId) {
        return userRepository.findById(userId)
                .map(u -> {
                    if (u.getFirstName() != null && !u.getFirstName().isBlank()) {
                        return u.getFirstName() + (u.getLastName() != null && !u.getLastName().isBlank() ? " " + u.getLastName() : "");
                    }
                    return u.getTelegramUsername() != null ? u.getTelegramUsername() : "Player #" + userId;
                })
                .orElse("Player #" + userId);
    }

    private void notify(Long userId, String type, String title, String body,
                        String referenceType, Long referenceId, String telegramText) {
        notify(userId, type, title, body, referenceType, referenceId, telegramText, null);
    }

    private void notify(Long userId, String type, String title, String body,
                        String referenceType, Long referenceId, String telegramText, String messageParams) {
        try {
            notificationService.notify(userId, type, title, body, keyForType(type), messageParams,
                    referenceType, referenceId, telegramText);
        } catch (Exception e) {
            log.warn("Failed to create notification (type={}, userId={}): {}", type, userId, e.getMessage());
        }
    }

    private String keyForType(String type) {
        return switch (type) {
            case "MIN_WITHDRAWAL" -> "notify.wallet.minWithdrawal";
            case "MISSING_PAYMENT_SCREENSHOT" -> "notify.wallet.missingScreenshot";
            case "DEPOSIT_REQUEST" -> "notify.wallet.depositRequested";
            case "DEPOSIT_APPROVED" -> "notify.wallet.depositApproved";
            case "DEPOSIT_REJECTED" -> "notify.wallet.depositRejected";
            case "WITHDRAWAL_REQUEST" -> "notify.wallet.withdrawalRequested";
            case "WITHDRAWAL_APPROVED" -> "notify.wallet.withdrawalApproved";
            case "WITHDRAWAL_REJECTED" -> "notify.wallet.withdrawalRejected";
            case "PLAYER_FUNDED" -> "notify.wallet.playerFunded";
            case "WIN" -> "notify.wallet.win";
            case "COMMISSION_CREDITED" -> "notify.wallet.commission";
            case "OWNER_FEE_SETTLEMENT" -> "notify.wallet.feeSplit";
            case "OWNER_FEE_SETTLEMENT_APPROVED" -> "notify.wallet.feeApproved";
            case "OWNER_FEE_SETTLEMENT_REJECTED" -> "notify.wallet.feeRejected";
            default -> null;
        };
    }

    private void notifyMinWithdrawalViolation(Long playerId, BigDecimal requested, BigDecimal minimum) {
        String requestedStr = fmt(requested);
        String minStr = fmt(minimum);
        notify(playerId, "MIN_WITHDRAWAL",
                "Withdrawal below minimum",
                "Minimum withdrawal is " + minStr + " coins. You requested " + requestedStr + " coins, so the request was not submitted.",
                "WITHDRAWAL", null,
                "\u26A0\uFE0F Withdrawal below minimum\nMinimum: " + minStr + " coins, requested: " + requestedStr + ".\nRaise the amount and try again in the app.",
                "{\"min\":\"" + minStr + "\",\"requested\":\"" + requestedStr + "\"}");
    }

    private void notifyMissingPaymentScreenshot(Long playerId, BigDecimal amount) {
        notify(playerId, "MISSING_PAYMENT_SCREENSHOT",
                "Payment screenshot required",
                "Your deposit request of " + fmt(amount) + " coins was not submitted because no payment screenshot was attached. Attach your payment proof and try again.",
                "COIN_REQUEST", null,
                "\uD83D\uDDBC\uFE0F Screenshot required\nAttach your payment screenshot and submit the deposit of " + fmt(amount) + " coins again in the app.",
                "{\"amount\":\"" + fmt(amount) + "\"}");
    }

    private void notifyDepositRequested(Player player, CoinRequest request) {
        Long adminUserId = player.getAdminUserId();
        if (adminUserId == null) return;
        String playerName = playerDisplayName(request.getUserId());
        notify(adminUserId, "DEPOSIT_REQUEST",
                "New deposit request",
                playerName + " requested a deposit of " + fmt(request.getAmount()) + " coins.",
                "COIN_REQUEST", request.getId(),
                "\uD83D\uDCC8 Deposit request\n" + playerName + " wants " + fmt(request.getAmount()) + " coins. Approve it in the app.",
                "{\"name\":\"" + playerName.replace("\"", "") + "\",\"amount\":\"" + fmt(request.getAmount()) + "\"}");
    }

    private void notifyDepositApproved(CoinRequest request) {
        String amount = fmt(request.getAmount());
        notify(request.getUserId(), "DEPOSIT_APPROVED",
                "Deposit approved",
                "Your deposit of " + amount + " coins has been approved and credited to your balance.",
                "COIN_REQUEST", request.getId(),
                "\u2705 Deposit approved\n" + amount + " coins have been added to your balance. Good luck!",
                "{\"amount\":\"" + amount + "\"}");
    }

    private void notifyDepositRejected(CoinRequest request, String reason) {
        if (request == null) return;
        String amount = fmt(request.getAmount());
        String why = (reason == null || reason.isBlank()) ? "Please contact support." : reason;
        notify(request.getUserId(), "DEPOSIT_REJECTED",
                "Deposit rejected",
                "Your deposit of " + amount + " coins was rejected. Reason: " + why,
                "COIN_REQUEST", request.getId(),
                "\u274C Deposit rejected\n" + amount + " coins request was declined.\nReason: " + why,
                "{\"amount\":\"" + amount + "\",\"reason\":\"" + why.replace("\"", "") + "\"}");
    }

    private void notifyWithdrawalRequested(Player player, Withdrawal withdrawal) {
        Long adminUserId = player.getAdminUserId();
        if (adminUserId == null) return;
        String playerName = playerDisplayName(withdrawal.getUserId());
        notify(adminUserId, "WITHDRAWAL_REQUEST",
                "New withdrawal request",
                playerName + " requested a withdrawal of " + fmt(withdrawal.getAmount()) + " coins.",
                "WITHDRAWAL", withdrawal.getId(),
                "\uD83D\uDCB5 Withdrawal request\n" + playerName + " wants to withdraw " + fmt(withdrawal.getAmount()) + " coins. Review it in the app.",
                "{\"name\":\"" + playerName.replace("\"", "") + "\",\"amount\":\"" + fmt(withdrawal.getAmount()) + "\"}");
    }

    private void notifyWithdrawalApproved(Withdrawal withdrawal) {
        String amount = fmt(withdrawal.getAmount());
        notify(withdrawal.getUserId(), "WITHDRAWAL_APPROVED",
                "Withdrawal paid",
                "Your withdrawal of " + amount + " coins has been approved and paid out.",
                "WITHDRAWAL", withdrawal.getId(),
                "\u2705 Withdrawal paid\n" + amount + " coins have been paid out. Thank you for playing!",
                "{\"amount\":\"" + amount + "\"}");
    }

    private void notifyWithdrawalRejected(Withdrawal withdrawal, String reason) {
        String amount = fmt(withdrawal.getAmount());
        String why = (reason == null || reason.isBlank()) ? "Please contact support." : reason;
        notify(withdrawal.getUserId(), "WITHDRAWAL_REJECTED",
                "Withdrawal rejected",
                "Your withdrawal of " + amount + " coins was rejected. Reason: " + why,
                "WITHDRAWAL", withdrawal.getId(),
                "\u274C Withdrawal rejected\n" + amount + " coins.\nReason: " + why,
                "{\"amount\":\"" + amount + "\",\"reason\":\"" + why.replace("\"", "") + "\"}");
    }

    private void notifyPlayerFunded(Long playerId, Long adminUserId, BigDecimal amount) {
        notify(playerId, "PLAYER_FUNDED",
                "You received coins",
                "You received " + fmt(amount) + " coins from " + playerDisplayName(adminUserId) + ".",
                null, null,
                "\uD83D\uDCB0 Coins credited\n" + fmt(amount) + " coins have been added to your balance.",
                "{\"amount\":\"" + fmt(amount) + "\",\"from\":\"" + playerDisplayName(adminUserId).replace("\"", "") + "\"}");
    }

    private void notifyGameWin(Long playerId, BigDecimal amount, Long gameId) {
        notify(playerId, "WIN",
                "You won!",
                "Congratulations! You won " + fmt(amount) + " coins.",
                "GAME", gameId,
                "\uD83C\uDF89 You won!\n" + fmt(amount) + " coins were credited to your balance. Congrats!",
                "{\"amount\":\"" + fmt(amount) + "\"}");
    }

    private void notifyCommissionCredited(Long adminUserId, BigDecimal amount, Long gameId) {
        notify(adminUserId, "COMMISSION_CREDITED",
                "Commission credited",
                "You earned " + fmt(amount) + " coins as commission from Game #" + gameId + ".",
                "GAME", gameId, null,
                "{\"amount\":\"" + fmt(amount) + "\",\"gameId\":\"" + gameId + "\"}");
    }

    /**
     * Total commission earned by the current tenant's agent (scoped to the active
     * tenant context). Used for platform-level super-admin reporting.
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public BigDecimal getTotalCommissionInTenant() {
        return sumAmounts(transactionRepository.findByType(TransactionType.AGENT_COMMISSION.name()))
                .add(sumAmounts(transactionRepository.findByType(TransactionType.UNCLAIMED_PRIZE.name())));
    }

    /** Total owner (platform) revenue from settled games in the active tenant. */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public BigDecimal getTotalPlatformFeeInTenant() {
        return sumAmounts(transactionRepository.findByType(TransactionType.PLATFORM_FEE.name()));
    }

    private BigDecimal sumAmounts(List<Transaction> txns) {
        return txns.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
