package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.master.entity.CardRequest;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.FundStatus;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.CardRequestRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.exception.RequestAlreadyProcessedException;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.tenant.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardRequestService {

    private static final int MIN_QUANTITY = 50;

    private final CardRequestRepository cardRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CardService cardService;

    /**
     * An agent asks the platform (super admin) for more cards.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public CardRequest requestCardStock(Long adminUserId, Integer quantity) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new WalletException("Admin not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new WalletException("Only admins can request cards");
        }
        if (quantity == null || quantity < MIN_QUANTITY) {
            throw new WalletException("Quantity must be at least " + MIN_QUANTITY);
        }

        if (cardRequestRepository.findFirstByAdminUserIdAndStatusOrderByCreatedAtDesc(adminUserId, FundStatus.PENDING).isPresent()) {
            throw new WalletException("You already have a pending card request",
                    "Your previous card request is still waiting for approval.");
        }

        CardRequest request = CardRequest.builder()
                .adminUserId(adminUserId)
                .quantity(quantity)
                .status(FundStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        CardRequest saved = cardRequestRepository.save(request);

        notificationService.notify(adminUserId, "CARD_REQUEST_SUBMITTED", "Card request submitted",
                "Your request for " + quantity + " new cards is awaiting super admin approval.",
                "notify.cardRequest.submitted", "{\"quantity\":" + quantity + "}",
                "CARD_REQUEST", saved.getId());

        log.info("Admin {} requested {} cards from super admin", adminUserId, quantity);
        return saved;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<CardRequest> listForAgent(Long adminUserId) {
        return cardRequestRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId);
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<CardRequest> listPending() {
        return cardRequestRepository.findByStatusOrderByCreatedAtDesc(FundStatus.PENDING);
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<CardRequest> listAll() {
        return cardRequestRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Approve → generate the requested cards inside the agent's tenant database.
     * Not @Transactional: the request lives in the master DB while the cards are
     * generated in the agent's tenant DB, and the two cannot share one transaction.
     */
    public void approveCardRequest(Long requestId, Long superAdminId) {
        User superAdmin = userRepository.findById(superAdminId)
                .orElseThrow(() -> new WalletException("Super admin not found"));
        if (superAdmin.getRole() != Role.SUPER_ADMIN) {
            throw new WalletException("Only super admin can approve card requests");
        }

        CardRequest request = cardRequestRepository.findById(requestId)
                .orElseThrow(() -> new WalletException("Card request not found"));
        if (request.getStatus() != FundStatus.PENDING) {
            throw new RequestAlreadyProcessedException("Request already processed");
        }

        User admin = userRepository.findById(request.getAdminUserId())
                .orElseThrow(() -> new WalletException("Admin not found"));

        // Atomic claim: concurrent/double approval can never proceed past this line.
        if (cardRequestRepository.claimForProcessing(requestId, FundStatus.APPROVED,
                FundStatus.PENDING, superAdminId, LocalDateTime.now(), null) == 0) {
            throw new RequestAlreadyProcessedException("Request already processed");
        }

        try {
            TenantHelper.runWithTenant(admin, () -> cardService.generateCardPool(request.getQuantity()));
        } catch (Exception e) {
            // Compensation: do not leave the request approved if the cards were not generated.
            cardRequestRepository.claimForProcessing(requestId, FundStatus.PENDING,
                    FundStatus.APPROVED, null, null, null);
            throw new RuntimeException("Failed to generate cards, request reverted to pending", e);
        }

        notificationService.notify(admin.getId(), "CARDS_ADDED", "New cards added",
                request.getQuantity() + " new cards were added to your pool by super admin.",
                "notify.cards.added", "{\"quantity\":" + request.getQuantity() + "}",
                "CARD_REQUEST", requestId,
                "Your card pool has been topped up with " + request.getQuantity() + " new cards.");

        log.info("Card request {} approved by super admin {} — {} cards generated for admin {}",
                requestId, superAdminId, request.getQuantity(), request.getAdminUserId());
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public void rejectCardRequest(Long requestId, Long superAdminId, String reason) {
        if (cardRequestRepository.claimForProcessing(requestId, FundStatus.REJECTED,
                FundStatus.PENDING, superAdminId, LocalDateTime.now(), reason) == 0) {
            throw new RequestAlreadyProcessedException("Request already processed");
        }

        var rejected = cardRequestRepository.findById(requestId).orElse(null);
        if (rejected != null) {
            notificationService.notify(rejected.getAdminUserId(), "CARD_REQUEST_REJECTED", "Card request rejected",
                    "Your request for " + rejected.getQuantity() + " cards was rejected"
                            + (reason != null && !reason.isBlank() ? ": " + reason : "."),
                    "notify.cardRequest.rejected", "{\"quantity\":" + rejected.getQuantity() + ",\"reason\":\""
                            + (reason != null ? reason.replace("\"", "") : "") + "\"}",
                    "CARD_REQUEST", requestId);
        }

        log.info("Card request {} rejected by super admin {}: {}", requestId, superAdminId, reason);
    }
}