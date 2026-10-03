package com.bingo.app.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final Map<Object, Object> tenantDataSources = new ConcurrentHashMap<>();

    public TenantRoutingDataSource(String baseUrl, String username, String password, String masterDatabase) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;

        // Extract base URL without database name
        String baseDbUrl = baseUrl.replaceAll("/[^/]+$", "/");
        String masterUrl = baseDbUrl + masterDatabase;

        HikariDataSource masterDs = createDataSource(masterUrl);
        tenantDataSources.put("master", masterDs);

        setDefaultTargetDataSource(masterDs);
        setTargetDataSources(tenantDataSources);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenant();
    }

    public void addTenant(String tenantId, String databaseName) {
        if (!tenantDataSources.containsKey(tenantId)) {
            String baseDbUrl = baseUrl.replaceAll("/[^/]+$", "/");
            String dbUrl = baseDbUrl + databaseName;
            HikariDataSource ds = createDataSource(dbUrl);
            tenantDataSources.put(tenantId, ds);
            setTargetDataSources(tenantDataSources);
            afterPropertiesSet();
        }
    }

    private HikariDataSource createDataSource(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("tenant-pool-" + System.currentTimeMillis());

        return new HikariDataSource(config);
    }

    /**
     * Forget a tenant and shut its pool. A dropped database keeps its pool
     * pointing at nothing, and the pool holds the very connections that stop
     * {@code DROP DATABASE} from succeeding, so both have to go together.
     */
    public void removeTenant(String tenantId) {
        Object removed = tenantDataSources.remove(tenantId);
        setTargetDataSources(tenantDataSources);
        afterPropertiesSet();
        if (removed instanceof HikariDataSource hikari) {
            try {
                hikari.close();
            } catch (Exception ignored) {
                // The pool is being discarded either way; a close failure here must
                // not stop the database drop that follows.
            }
        }
    }
}