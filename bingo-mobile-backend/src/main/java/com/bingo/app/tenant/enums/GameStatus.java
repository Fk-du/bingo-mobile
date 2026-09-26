package com.bingo.app.tenant.enums;

import java.util.List;

public enum GameStatus {
    REGISTRATION_OPEN,
    STARTING,
    IN_PROGRESS,
    PAUSED,
    CLAIM_PENDING,
    ENDED;

    /**
     * Statuses where a game is still live. Cards dealt to players of these games
     * are considered "occupied" and must not be offered to other players.
     */
    public static final List<GameStatus> ACTIVE = List.of(
            REGISTRATION_OPEN,
            STARTING,
            IN_PROGRESS,
            PAUSED,
            CLAIM_PENDING);
}