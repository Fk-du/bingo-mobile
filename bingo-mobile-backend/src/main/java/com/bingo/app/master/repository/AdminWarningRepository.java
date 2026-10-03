package com.bingo.app.master.repository;

import com.bingo.app.master.entity.AdminWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AdminWarningRepository extends JpaRepository<AdminWarning, Long> {

    List<AdminWarning> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);

    /** Warnings die with the agent they were issued to, so a delete leaves none behind. */
    @Modifying
    @Transactional(transactionManager = "masterTransactionManager")
    @Query("DELETE FROM AdminWarning w WHERE w.adminUserId = :adminUserId")
    int deleteByAdminUserId(@Param("adminUserId") Long adminUserId);
}
