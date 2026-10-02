-- Card previews. When a player asks to join a game they now see the cards
-- they would get BEFORE paying for any of them, and register the ones they
-- actually want one at a time. A previewed card is held for that player (so
-- nobody else is dealt the same numbers) but is not a registration: it has no
-- game_cards row, no entry fee charged, and no claim rights.
--
-- Previews therefore live in their own table rather than as a flag on
-- game_cards. Every existing game_cards query -- the prize pot, the player
-- count, the claim eligibility checks -- assumes each row is a paid
-- registration, and a half-registered row in that table would quietly change
-- all of them. An empty preview table keeps those queries exactly as they were.
--
-- This script runs once per TENANT database, so it must only touch tenant
-- tables.
CREATE TABLE IF NOT EXISTS card_previews (
    id         BIGSERIAL PRIMARY KEY,
    game_id    BIGINT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    player_id  BIGINT NOT NULL,
    card_id    BIGINT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- A card is held for at most one player per game, and a player never previews
-- the same card twice in one game.
CREATE UNIQUE INDEX IF NOT EXISTS uq_card_previews_game_card ON card_previews (game_id, card_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_card_previews_player_card ON card_previews (game_id, player_id, card_id);
CREATE INDEX IF NOT EXISTS idx_card_previews_player ON card_previews (player_id, game_id);
