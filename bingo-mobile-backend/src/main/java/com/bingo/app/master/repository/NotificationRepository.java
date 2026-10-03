package com.bingo.app.master.repository;

import com.bingo.app.master.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Notifications belong to the user row, so a delete of that row takes them with it. */
    @Modifying
    @Transactional(transactionManager = "masterTransactionManager")
    @Query("DELETE FROM Notification n WHERE n.userId IN :userIds")
    int deleteByUserIdIn(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId);
}