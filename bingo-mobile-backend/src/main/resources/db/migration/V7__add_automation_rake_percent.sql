-- The per-game commission percentage is gone: an admin now sets an absolute
-- prize (see V16) and keeps whatever the pot has left over. What survives of the
-- old setting is the rake the admin PREFERS, kept on their automation template
-- and used to pre-fill the suggested prize when they open a game.
--
-- These scripts run before SchemaMigrationHelper creates automation_config, so
-- the table is created here first; the helper's CREATE TABLE IF NOT EXISTS is
-- then a no-op.
--
-- Only plain DDL lives in this file. The copy of any existing commission_percent
-- values onto rake_percent needs a column check the script runner cannot do (it
-- splits on semicolons, so it cannot run a DO block), so SchemaMigrationHelper
-- carries that step and drops the old column afterwards.
CREATE TABLE IF NOT EXISTS automation_config (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    entry_fee DECIMAL(19,2),
    max_players INTEGER,
    call_interval INTEGER,
    rake_percent DECIMAL(19,2),
    winning_pattern VARCHAR(50),
    custom_pattern_name VARCHAR(255),
    custom_pattern_cells TEXT,
    auto_mark BOOLEAN NOT NULL DEFAULT TRUE,
    registration_window_seconds INTEGER,
    cooldown_seconds INTEGER,
    start_when_full BOOLEAN NOT NULL DEFAULT TRUE,
    next_game_at TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE automation_config ADD COLUMN IF NOT EXISTS rake_percent DECIMAL(19,2);
