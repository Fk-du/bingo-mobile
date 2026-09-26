-- No two players may hold the same card in the same game.
-- Reuse across different games is always allowed (unique is on the (game_id, card_id) pair).
CREATE UNIQUE INDEX IF NOT EXISTS uq_game_cards_game_card ON game_cards (game_id, card_id);

-- Preserve per-card win stats now that cards belong to the shared pool (not an account).
ALTER TABLE cards ADD COLUMN IF NOT EXISTS games_won INTEGER NOT NULL DEFAULT 0;