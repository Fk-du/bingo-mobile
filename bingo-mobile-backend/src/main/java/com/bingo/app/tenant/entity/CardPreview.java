package com.bingo.app.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A card held for a player to look at before they pay for it.
 *
 * <p>This is NOT a registration. There is no {@link GameCard} row, no entry fee
 * has been taken, and the card cannot claim Bingo. It exists so the player can
 * see the exact numbers they are about to buy and pick the ones they want.
 */
@Entity
@Table(name = "card_previews", indexes = {
        @Index(name = "idx_card_previews_player", columnList = "player_id, game_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardPreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "player_id")
    private Long playerId;

    @ManyToOne
    @JoinColumn(name = "card_id")
    private Card card;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
