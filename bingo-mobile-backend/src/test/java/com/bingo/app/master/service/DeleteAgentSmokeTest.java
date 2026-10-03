package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.infrastructure.persistence.TenantRoutingDataSource;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.exception.AdminDeletionException;
import com.bingo.app.master.repository.TenantRegistryRepository;
import com.bingo.app.master.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the agent delete against the real database: first that a running game
 * refuses it and changes nothing, then that a quiet agent goes with their tenant
 * database and their players.
 *
 * <p>Destructive, so it stays off unless pointed at a throwaway agent:
 * {@code mvn -o test -Dtest=DeleteAgentSmokeTest -Dtest.admin.phone=<phone>
 * -Dtest.super.admin.id=1}. It deletes whatever agent carries that phone number.
 */
@EnabledIfSystemProperty(named = "test.admin.phone", matches = ".+")
@SpringBootTest(properties = {
        "app.telegram.bot.token=",
        "app.telegram.bot.username=",
        "app.telegram.registration-bot.token=",
        "app.telegram.registration-bot.username=",
        "bingo.telegram.bot.token=",
        "bingo.telegram.bot.username="
})
class DeleteAgentSmokeTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRegistryRepository tenantRegistryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TenantRoutingDataSource routingDataSource;

    private static String phone() {
        return System.getProperty("test.admin.phone");
    }

    private static Long superAdminId() {
        return Long.valueOf(System.getProperty("test.super.admin.id", "1"));
    }

    @Test
    @DisplayName("a throwaway agent is deleted with its tenant database and players")
    void deletesThrowawayAgent() {
        Long adminId = userRepository.findByPhoneNumber(phone()).map(User::getId).orElseThrow();
        List<Long> players = userRepository.findAllByAdminUserId(adminId).stream().map(User::getId).toList();
        String database = tenantRegistryRepository.findByAdminUserId(adminId)
                .map(r -> r.getDatabaseName()).orElseThrow();
        entityManager.clear();

        System.out.println("[smoke] before: admin=" + adminId + " players=" + players
                + " tenant=" + database + " dbExists=" + databaseExists(database));

        // A running game has to stop the delete, with the agent and its database
        // left exactly as they were — that is the whole point of the guard.
        Long gameId = insertOpenGame(adminId);
        AdminDeletionException refusal = assertThrows(AdminDeletionException.class,
                () -> userService.deleteAdmin(adminId, superAdminId()));
        System.out.println("[smoke] refused while game " + gameId + " open: code="
                + refusal.getCode() + " message=" + refusal.getUserMessage());
        assertEquals("admin_has_open_games", refusal.getCode());
        assertTrue(userRepository.existsById(adminId), "agent row removed despite the refusal");
        assertTrue(databaseExists(database), "tenant dropped despite the refusal");
        deleteGame(adminId, gameId);

        var deleted = userService.deleteAdmin(adminId, superAdminId());
        entityManager.clear();

        System.out.println("[smoke] response: " + deleted);
        System.out.println("[smoke] after: dbExists=" + databaseExists(database));

        assertEquals(players.size(), deleted.playersRemoved());
        assertEquals(database, deleted.tenantDatabase());
        assertTrue(userRepository.findById(adminId).isEmpty(), "agent row still there");
        assertFalse(databaseExists(database), "tenant database was not dropped");
        assertTrue(tenantRegistryRepository.findByAdminUserId(adminId).isEmpty(), "registry row still there");
        for (Long playerId : players) {
            assertFalse(userRepository.existsById(playerId), "player " + playerId + " still there");
        }
        assertEquals(0, countNotifications(adminId));
    }

    /** A live game in that agent's own tenant database, which only this test can see. */
    private Long insertOpenGame(Long adminId) {
        JdbcTemplate tenantJdbc = new JdbcTemplate(routingDataSource);
        TenantContext.setTenant(TenantContext.tenantKeyForAdmin(adminId));
        try {
            return tenantJdbc.queryForObject(
                    "INSERT INTO games (admin_user_id, status, entry_fee, winning_pattern, call_interval) "
                            + "VALUES (?, 'IN_PROGRESS', 10, 'FULL_HOUSE', 5) RETURNING id",
                    Long.class, adminId);
        } finally {
            TenantContext.clear();
        }
    }

    private void deleteGame(Long adminId, Long gameId) {
        JdbcTemplate tenantJdbc = new JdbcTemplate(routingDataSource);
        TenantContext.setTenant(TenantContext.tenantKeyForAdmin(adminId));
        try {
            tenantJdbc.update("DELETE FROM games WHERE id = ?", gameId);
        } finally {
            TenantContext.clear();
        }
    }

    private long countNotifications(Long userId) {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM notifications WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult()).longValue();
    }

    private boolean databaseExists(String name) {
        Object result = entityManager.createNativeQuery(
                        "SELECT count(*) FROM pg_database WHERE datname = :name")
                .setParameter("name", name).getSingleResult();
        return ((Number) result).longValue() > 0;
    }
}