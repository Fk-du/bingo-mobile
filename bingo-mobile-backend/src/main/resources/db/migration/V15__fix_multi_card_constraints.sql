-- Multi-card support: players may hold several cards in the same game, so the
-- V11 (game_id, player_id) guard that allowed only ONE card per player is wrong.
-- The deal must be unique per physical card, not per player.

-- A game may sell the same physical card only once (already enforced by
-- uq_game_cards_game_card from V14) — drop the per-player cap that blocks a
-- second card for the same player.
ALTER TABLE game_cards DROP CONSTRAINT IF EXISTS uq_game_cards_game_player;

-- A player may claim on any of their cards (each claim is one candidate row),
-- so (game_id, player_id, result) wrongly rejects a second claim from the same
-- player. Keep at most one pending VALID claim per (game, card) instead.
ALTER TABLE bingo_claims DROP CONSTRAINT IF EXISTS uq_bingo_claims_game_player_result;
CREATE UNIQUE INDEX IF NOT EXISTS uq_bingo_claims_game_card_result
    ON bingo_claims (game_id, card_id) WHERE result = 'VALID';