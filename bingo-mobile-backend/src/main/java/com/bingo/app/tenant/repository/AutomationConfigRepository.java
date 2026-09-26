package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.AutomationConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AutomationConfigRepository extends JpaRepository<AutomationConfig, Long> {

    Optional<AutomationConfig> findByAdminUserId(Long adminUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AutomationConfig a WHERE a.adminUserId = :adminUserId")
    Optional<AutomationConfig> findByAdminUserIdForUpdate(@Param("adminUserId") Long adminUserId);
}