-- Custom winning patterns are gone: every pattern is now a fixed one from the canonical
-- GamePatterns list, so nothing reads or writes the drawn-cell columns any more.
ALTER TABLE games DROP COLUMN IF EXISTS custom_pattern_name;
ALTER TABLE games DROP COLUMN IF EXISTS custom_pattern_cells;
ALTER TABLE automation_config DROP COLUMN IF EXISTS custom_pattern_name;
ALTER TABLE automation_config DROP COLUMN IF EXISTS custom_pattern_cells;
