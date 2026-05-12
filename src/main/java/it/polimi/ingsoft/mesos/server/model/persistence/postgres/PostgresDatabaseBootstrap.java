package it.polimi.ingsoft.mesos.server.model.persistence.postgres;

import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton that bootstraps PostgreSQL at startup: creates the database if absent,
 * runs DDL, and verifies schema health.
 */
public final class PostgresDatabaseBootstrap {
    private final Set<String> initializedTargets = ConcurrentHashMap.newKeySet();

    private PostgresDatabaseBootstrap() {}

    public static PostgresDatabaseBootstrap getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Ensures the target database exists, is reachable, and contains every required table.
     * Idempotent: skips work when the same target + module set was already bootstrapped.
     */
    public void ensureReady(ServerRuntimeConfig runtimeConfig, List<PostgresSchemaModule> schemaModules) {
        Objects.requireNonNull(runtimeConfig, "Runtime config cannot be null");
        if (schemaModules == null || schemaModules.isEmpty())
            throw new IllegalArgumentException("Schema modules cannot be null or empty");

        String cacheKey = buildCacheKey(runtimeConfig, schemaModules);
        if (this.initializedTargets.contains(cacheKey)) return;

        synchronized (this) {
            if (this.initializedTargets.contains(cacheKey)) return;

            this.ensureDatabaseExists(runtimeConfig);
            this.verifyReachability(runtimeConfig.toPostgresConnectionConfig());
            this.initializeSchema(runtimeConfig.toPostgresConnectionConfig(), schemaModules);
            this.verifySchemaHealth(runtimeConfig.toPostgresConnectionConfig(), schemaModules);

            this.initializedTargets.add(cacheKey);
        }
    }

    private void ensureDatabaseExists(ServerRuntimeConfig config) {
        try (Connection conn = openConnection(config.adminJdbcUrl(), config.username(), config.password());
             PreparedStatement check = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            conn.setAutoCommit(true);
            check.setString(1, config.databaseName());
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return;
            }
            try (Statement create = conn.createStatement()) {
                create.execute("CREATE DATABASE " + quoteIdentifier(config.databaseName()));
            }
        } catch (SQLException e) {
            PostgresDatabaseHealthException.Reason reason = isConnectionFailure(e)
                    ? PostgresDatabaseHealthException.Reason.UNREACHABLE
                    : PostgresDatabaseHealthException.Reason.UNHEALTHY;
            String msg = reason == PostgresDatabaseHealthException.Reason.UNREACHABLE
                    ? "PostgreSQL admin database is not reachable: " + e.getMessage()
                    : "PostgreSQL is reachable but the target database cannot be created: " + e.getMessage();
            throw new PostgresDatabaseHealthException(reason, msg, e);
        }
    }

    private void verifyReachability(PostgresConnectionConfig conn) {
        try (Connection c = openConnection(conn.jdbcUrl(), conn.username(), conn.password());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1")) {
            if (!rs.next() || rs.getInt(1) != 1)
                throw new PostgresDatabaseHealthException(
                        PostgresDatabaseHealthException.Reason.UNHEALTHY,
                        "PostgreSQL responded but health probe returned an unexpected result"
                );
        } catch (SQLException e) {
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNREACHABLE,
                    "PostgreSQL target database is not reachable: " + e.getMessage(), e
            );
        }
    }

    private void initializeSchema(PostgresConnectionConfig conn, List<PostgresSchemaModule> modules) {
        try (Connection c = openConnection(conn.jdbcUrl(), conn.username(), conn.password());
             Statement s = c.createStatement()) {
            for (PostgresSchemaModule module : modules) {
                for (String ddl : module.createStatements()) {
                    s.execute(ddl);
                }
            }
        } catch (SQLException e) {
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNHEALTHY,
                    "PostgreSQL schema initialization failed: " + e.getMessage(), e
            );
        }
    }

    private void verifySchemaHealth(PostgresConnectionConfig conn, List<PostgresSchemaModule> modules) {
        Set<String> missing = new LinkedHashSet<>();
        try (Connection c = openConnection(conn.jdbcUrl(), conn.username(), conn.password());
             PreparedStatement s = c.prepareStatement("""
                     SELECT 1 FROM information_schema.tables
                     WHERE table_schema = current_schema() AND table_name = ?
                     """)) {
            for (PostgresSchemaModule module : modules) {
                for (String table : module.requiredTables()) {
                    s.setString(1, table);
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next()) missing.add(table);
                    }
                }
            }
        } catch (SQLException e) {
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNHEALTHY,
                    "PostgreSQL schema verification failed: " + e.getMessage(), e
            );
        }
        if (!missing.isEmpty())
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNHEALTHY,
                    "PostgreSQL schema is not healthy. Missing tables: " + missing
            );
    }

    private static Connection openConnection(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static boolean isConnectionFailure(SQLException e) {
        String state = e.getSQLState();
        return state == null || state.startsWith("08");
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String buildCacheKey(ServerRuntimeConfig config, List<PostgresSchemaModule> modules) {
        String fingerprint = modules.stream()
                .map(PostgresSchemaModule::moduleName)
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        return config.jdbcUrl() + "|" + fingerprint;
    }

    private static final class Holder {
        private static final PostgresDatabaseBootstrap INSTANCE = new PostgresDatabaseBootstrap();
    }
}
