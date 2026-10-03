package com.bingo.app.master.repository;

import com.bingo.app.master.entity.TenantRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TenantRegistryRepository extends JpaRepository<TenantRegistry, Long> {

    Optional<TenantRegistry> findByAdminUserId(Long adminUserId);

    boolean existsByAdminUserId(Long adminUserId);

    Optional<TenantRegistry> findByDatabaseName(String databaseName);

    @Modifying
    @Transactional(transactionManager = "masterTransactionManager")
    @Query("DELETE FROM TenantRegistry t WHERE t.adminUserId = :adminUserId")
    int deleteByAdminUserId(@Param("adminUserId") Long adminUserId);
}
