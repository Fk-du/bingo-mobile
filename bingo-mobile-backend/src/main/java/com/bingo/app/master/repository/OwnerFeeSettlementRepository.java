package com.bingo.app.master.repository;

import com.bingo.app.master.entity.OwnerFeeSettlement;
import com.bingo.app.master.enums.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerFeeSettlementRepository extends JpaRepository<OwnerFeeSettlement, Long> {

    List<OwnerFeeSettlement> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);

    List<OwnerFeeSettlement> findByStatusOrderByCreatedAtDesc(FundStatus status);

    List<OwnerFeeSettlement> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM OwnerFeeSettlement r " +
            "WHERE r.adminUserId = :adminUserId AND r.status = 'APPROVED'")
    BigDecimal sumApprovedByAdmin(@Param("adminUserId") Long adminUserId);

    Optional<OwnerFeeSettlement> findTopByAdminUserIdAndStatusOrderByApprovedAtDesc(Long adminUserId, FundStatus status);

    /**
     * Atomically moves a PENDING settlement to the target status. Returns 0 when the
     * settlement was already processed, which makes concurrent/double approvals impossible.
     */
    @Modifying
    @Transactional(transactionManager = "masterTransactionManager")
    @Query("UPDATE OwnerFeeSettlement r SET r.status = :status, r.approvedBy = :approvedBy, " +
            "r.approvedAt = :approvedAt, r.rejectionReason = :rejectionReason " +
            "WHERE r.id = :id AND r.status = :pending")
    int claimForProcessing(@Param("id") Long id,
                           @Param("status") FundStatus status,
                           @Param("pending") FundStatus pending,
                           @Param("approvedBy") Long approvedBy,
                           @Param("approvedAt") LocalDateTime approvedAt,
                           @Param("rejectionReason") String rejectionReason);
}