package com.bingo.app.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public final class SchemaMigrationHelper {

    private SchemaMigrationHelper() {
    }

    public static void runMasterMigrations(Connection conn) throws SQLException {
        // Fund request channel has been removed; drop the table if it still exists.
        execute(conn, "DROP TABLE IF EXISTS admin_fund_requests CASCADE");

        renameColumnIfExists(conn, "users", "agent_id", "admin_user_id");
        renameColumnIfExists(conn, "tenant_registry", "agent_id", "admin_user_id");

        addColumnIfNotExists(conn, "users", "password_hash", "VARCHAR(255)");
        addColumnIfNotExists(conn, "users", "business_name", "VARCHAR(255)");
        addColumnIfNotExists(conn, "users", "admin_approved", "BOOLEAN NOT NULL DEFAULT FALSE");
        ensureUserColumnBoolean(conn, "users", "admin_approved");
        addColumnIfNotExists(conn, "users", "active", "BOOLEAN NOT NULL DEFAULT TRUE");
        ensureUserColumnBoolean(conn, "users", "active");
        addColumnIfNotExists(conn, "users", "preferred_language", "VARCHAR(10) NOT NULL DEFAULT 'en'");
        createUniqueIndexWhereNotNull(conn, "uk_users_phone_number", "users", "phone_number");
        addColumnIfNotExists(conn, "notifications", "message_key", "VARCHAR(100)");
        addColumnIfNotExists(conn, "notifications", "message_params", "TEXT");

        migrateAgentsIntoUsers(conn);

        dropIndexIfExists(conn, "idx_users_agent");
        createIndexIfNotExists(conn, "idx_users_admin", "users", "admin_user_id");

        ensureTenantRegistryDatabaseName(conn);

        dropIndexIfExists(conn, "idx_tenant_registry_agent");
        createIndexIfNotExists(conn, "idx_tenant_registry_admin", "tenant_registry", "admin_user_id");

        migrateOwnerShareRateToOwnerFee(conn);
    }

    /**
     * The owner's cut of each admin's commission is now {@code ownerFeePercent},
     * defaulting to 30%. It was {@code ownerShareRate} at 20%; the super admin
     * can change it from the app's config screen either way.
     */
    private static void migrateOwnerShareRateToOwnerFee(Connection conn) throws SQLException {
        if (!tableExists(conn, "platform_config")) {
            return;
        }
        try (var ps = conn.prepareStatement("""
                UPDATE platform_config SET value = '30' WHERE key = 'ownerShareRate'
                """)) {
            ps.executeUpdate();
        }
        try (var ps = conn.prepareStatement("DELETE FROM platform_config WHERE key = 'ownerShareRate'")) {
            ps.executeUpdate();
        }
        upsertConfig(conn, "ownerFeePercent", "30");
        upsertConfig(conn, "minPrizePercent", "50");
        upsertConfig(conn, "maxPrizePercent", "90");
    }

    private static void upsertConfig(Connection conn, String key, String value) throws SQLException {
        try (var ps = conn.prepareStatement("""
                INSERT INTO platform_config (key, value) VALUES (?, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public static void runTenantMigrations(Connection conn) throws SQLException {
        renameColumnIfExists(conn, "players", "agent_id", "admin_user_id");
        renameColumnIfExists(conn, "games", "agent_id", "admin_user_id");

        dropIndexIfExists(conn, "idx_players_agent");
        createIndexIfNotExists(conn, "idx_players_admin", "players", "admin_user_id");

        dropIndexIfExists(conn, "idx_games_agent_status");
        createIndexIfNotExists(conn, "idx_games_admin_status", "games", "admin_user_id, status");

        // Per-admin game automation template (auto-create + auto-start).
        createTableIfNotExists(conn, """
                CREATE TABLE IF NOT EXISTS automation_config (
                    id BIGSERIAL PRIMARY KEY,
                    admin_user_id BIGINT UNIQUE,
                    enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    entry_fee DECIMAL(19,2),
                    call_interval INTEGER,
                    rake_percent DECIMAL(19,2),
                    winning_pattern VARCHAR(50),
                    auto_mark BOOLEAN NOT NULL DEFAULT TRUE,
                    registration_window_seconds INTEGER,
                    cooldown_seconds INTEGER,
                    next_game_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        createIndexIfNotExists(conn, "idx_automation_admin", "automation_config", "admin_user_id");

        // Auto claim review (auto approve/reject) opt-in and grace window.
        addColumnIfNotExists(conn, "automation_config", "auto_review", "BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfNotExists(conn, "automation_config", "review_grace_seconds", "INTEGER NOT NULL DEFAULT 2");

        // The per-game commission percentage is replaced by an admin-chosen prize.
        // These two run after the SQL scripts (V7 / V16) and before the data
        // migration below, so the new columns exist even on a fresh tenant.
        addColumnIfNotExists(conn, "games", "prize_amount", "NUMERIC(12,2)");
        addColumnIfNotExists(conn, "automation_config", "rake_percent", "DECIMAL(19,2)");

        // Custom patterns are gone: every winning pattern is now a fixed one from the
        // canonical list, so nothing writes these columns any more.
        dropColumnIfExists(conn, "games", "custom_pattern_name");
        dropColumnIfExists(conn, "games", "custom_pattern_cells");
        dropColumnIfExists(conn, "automation_config", "custom_pattern_name");
        dropColumnIfExists(conn, "automation_config", "custom_pattern_cells");
        migrateCommissionToPrize(conn);
    }

    /**
     * Carry live games onto the prize model at the same effective payout, so an
     * in-flight game does not change what its winners receive. Finished games are
     * left alone: they are history, and the old column recorded what they paid.
     */
    private static void migrateCommissionToPrize(Connection conn) throws SQLException {
        if (columnExists(conn, "games", "commission_percent")) {
            execute(conn, """
                    UPDATE games
                    SET prize_amount = ROUND(prize_pool * (1 - commission_percent / 100.0), 2)
                    WHERE prize_amount IS NULL
                      AND commission_percent IS NOT NULL
                      AND status IN ('REGISTRATION_OPEN', 'STARTING', 'IN_PROGRESS', 'PAUSED', 'CLAIM_PENDING')
                    """);
            execute(conn, "ALTER TABLE games DROP COLUMN IF EXISTS commission_percent");
            log.info("Migrated games.commission_percent to games.prize_amount");
        }

        if (tableExists(conn, "automation_config") && columnExists(conn, "automation_config", "commission_percent")) {
            execute(conn, "UPDATE automation_config SET rake_percent = commission_percent WHERE rake_percent IS NULL");
            execute(conn, "ALTER TABLE automation_config DROP COLUMN IF EXISTS commission_percent");
            log.info("Migrated automation_config.commission_percent to rake_percent");
        }
    }

    static void createTableIfNotExists(Connection conn, String createSql) throws SQLException {
        execute(conn, createSql);
    }

    private static void migrateAgentsIntoUsers(Connection conn) throws SQLException {
        if (!tableExists(conn, "agents")) {
            return;
        }

        execute(conn, """
                UPDATE users u
                SET business_name = COALESCE(u.business_name, ap.business_name),
                    admin_approved = CASE WHEN ap.approved THEN TRUE ELSE COALESCE(u.admin_approved, FALSE) END
                FROM agents ap
                WHERE ap.user_id = u.id
                """);

        execute(conn, "DROP TABLE agents");
        log.info("Migrated agents table into users and dropped agents");
    }

    static boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (var ps = conn.prepareStatement("""
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (var ps = conn.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static void renameColumnIfExists(Connection conn, String table, String from, String to) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        if (columnExists(conn, table, from) && !columnExists(conn, table, to)) {
            execute(conn, "ALTER TABLE " + table + " RENAME COLUMN " + from + " TO " + to);
            log.info("Renamed column {}.{} -> {}", table, from, to);
        }
    }

    private static void ensureTenantRegistryDatabaseName(Connection conn) throws SQLException {
        if (!tableExists(conn, "tenant_registry")) {
            return;
        }

        addColumnIfNotExists(conn, "tenant_registry", "database_name", "VARCHAR(255)");

        // Backfill null database_name using admin_user_id (fallback to id if admin_user_id is null)
        execute(conn, """
                UPDATE tenant_registry
                SET database_name = 'bingo_agent_' || COALESCE(admin_user_id::text, id::text)
                WHERE database_name IS NULL
                """);

        // Ensure NOT NULL constraint only after verifying no nulls remain
        execute(conn, """
                ALTER TABLE tenant_registry
                ALTER COLUMN database_name SET NOT NULL
                """);

        // Add unique constraint if missing
        execute(conn, """
                DO $$ BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint
                        WHERE conname = 'uk_tenant_registry_database_name'
                        AND conrelid = 'tenant_registry'::regclass
                    ) THEN
                        ALTER TABLE tenant_registry
                        ADD CONSTRAINT uk_tenant_registry_database_name UNIQUE (database_name);
                    END IF;
                END $$;
                """);

        log.info("Ensured database_name column on tenant_registry");
    }

    private static void ensureUserColumnBoolean(Connection conn, String table, String column) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        // Backfill null values with false
        execute(conn, "UPDATE " + table + " SET " + column + " = FALSE WHERE " + column + " IS NULL");
        log.info("Backfilled null {}.{} with FALSE", table, column);
        // Ensure NOT NULL
        execute(conn, "ALTER TABLE " + table + " ALTER COLUMN " + column + " SET NOT NULL");
        log.info("Ensured NOT NULL on {}.{}", table, column);
    }

    static void addColumnIfNotExists(Connection conn, String table, String column, String definition) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        if (!columnExists(conn, table, column)) {
            execute(conn, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("Added column {}.{}", table, column);
        }
    }

    static void dropColumnIfExists(Connection conn, String table, String column) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        if (columnExists(conn, table, column)) {
            execute(conn, "ALTER TABLE " + table + " DROP COLUMN " + column);
            log.info("Dropped column {}.{}", table, column);
        }
    }

    static void createIndexIfNotExists(Connection conn, String indexName, String table, String columns) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        execute(conn, "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + columns + ")");
    }

    /**
     * Creates a partial unique index on a nullable column. Postgres UNIQUE treats
     * NULLs as distinct, so this safely allows many rows with a NULL value while
     * rejecting duplicates among non-null values (used for users.phone_number).
     */
    static void createUniqueIndexWhereNotNull(Connection conn, String indexName, String table, String column) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        execute(conn, "CREATE UNIQUE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + column + ") WHERE " + column + " IS NOT NULL");
    }

    static void dropIndexIfExists(Connection conn, String indexName) throws SQLException {
        execute(conn, "DROP INDEX IF EXISTS " + indexName);
    }

    static void execute(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
