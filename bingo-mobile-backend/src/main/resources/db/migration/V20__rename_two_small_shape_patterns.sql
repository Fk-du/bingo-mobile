-- The "two small shapes + a disconnected line" patterns were coded as THREE_SMALL_*
-- even though they settle on two shapes plus a line, and the line cannot touch either
-- shape. Rename the codes to say what they mean and carry existing games and
-- automation preferences over. The UPDATEs are idempotent so re-running this script
-- on a tenant that already applied it is a no-op.
UPDATE games SET winning_pattern = 'TWO_SMALL_T_PLUS_LINE' WHERE winning_pattern = 'THREE_SMALL_T';
UPDATE games SET winning_pattern = 'TWO_SMALL_CROSSES_PLUS_LINE' WHERE winning_pattern = 'THREE_SMALL_CROSSES';
UPDATE automation_config SET winning_pattern = 'TWO_SMALL_T_PLUS_LINE' WHERE winning_pattern = 'THREE_SMALL_T';
UPDATE automation_config SET winning_pattern = 'TWO_SMALL_CROSSES_PLUS_LINE' WHERE winning_pattern = 'THREE_SMALL_CROSSES';