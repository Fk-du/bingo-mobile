package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.CardPreview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardPreviewRepository extends JpaRepository<CardPreview, Long> {

    List<CardPreview> findByGameIdAndPlayerIdOrderByCreatedAtAsc(Long gameId, Long playerId);

    Optional<CardPreview> findByGameIdAndPlayerIdAndCardId(Long gameId, Long playerId, Long cardId);

    Optional<CardPreview> findByGameIdAndCardId(Long gameId, Long cardId);

    long countByGameIdAndPlayerId(Long gameId, Long playerId);

    void deleteByGameIdAndPlayerId(Long gameId, Long playerId);

    /**
     * Drop every hold in a game. Called when the game stops accepting
     * registrations: a preview can never be registered after that point, so the
     * rows are dead weight, and leaving them would keep cards reserved for a game
     * that will never pay for them.
     */
    void deleteByGameId(Long gameId);
}
