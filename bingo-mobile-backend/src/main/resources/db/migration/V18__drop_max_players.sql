-- The max-players cap is gone: registration stays open until the admin starts the
-- game, so there is no table size to configure (or auto-start on). Drop the column
-- from games and the automation template, along with the start-when-full flag that
-- only existed to react to it.
ALTER TABLE games DROP COLUMN IF EXISTS max_players;
ALTER TABLE automation_config DROP COLUMN IF EXISTS max_players;
ALTER TABLE automation_config DROP COLUMN IF EXISTS start_when_full;
