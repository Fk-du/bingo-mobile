package com.bingo.app.master.repository;

import com.bingo.app.master.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);

    List<PasswordResetCode> findAllByUserId(Long userId);

    @Modifying
    @Query("UPDATE PasswordResetCode c SET c.used = true WHERE c.id = :id AND c.used = false")
    int markUsed(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PasswordResetCode c SET c.used = true WHERE c.userId = :userId AND c.used = false")
    int invalidateAll(@Param("userId") Long userId);
}