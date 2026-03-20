package it.polimi.ingsoft.mesos.server.persistence.postgres;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
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
 * Singleton responsible for bootstrapping PostgreSQL and verifying schema health at application startup.
 */
public final class PostgresDatabaseBootstrap {
    private final Set<String> initializedTargets;

    /**
     * Creates the singleton bootstrap and initializes the per-target bootstrap cache.
     */
    private PostgresDatabaseBootstrap() {
        this.initializedTargets = ConcurrentHashMap.newKeySet();
    }

    /**
     * Returns the shared singleton instance.
     *
     * @return singleton bootstrap instance
     */
    public static PostgresDatabaseBootstrap getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Ensures the target database exists, is reachable, and contains every required table.
     *
     * @param runtimeConfig runtime configuration
     * @param schemaModules modular schema definitions to initialize and verify
     */
    public void ensureReady(ServerRuntimeConfig runtimeConfig, List<PostgresSchemaModule> schemaModules) {
        Objects.requireNonNull(runtimeConfig, "Runtime config cannot be null");
        this.validateSchemaModules(schemaModules);

        String cacheKey = this.buildCacheKey(runtimeConfig, schemaModules);
        if (this.initializedTargets.contains(cacheKey)) {
            return;
        }

        synchronized (this) {
            if (this.initializedTargets.contains(cacheKey)) {
                return;
            }

            this.ensureDatabaseExists(runtimeConfig);
            this.verifyReachability(runtimeConfig.toPostgresConnectionConfig());
            this.initializeSchema(runtimeConfig.toPostgresConnectionConfig(), schemaModules);
            this.verifySchemaHealth(runtimeConfig.toPostgresConnectionConfig(), schemaModules);

            this.initializedTargets.add(cacheKey);
        }
    }

    /**
     * Validates that at least one schema module has been provided.
     */
    private void validateSchemaModules(List<PostgresSchemaModule> schemaModules) {
        if (schemaModules == null || schemaModules.isEmpty()) {
            throw new IllegalArgumentException("Schema modules cannot be null or empty");
        }
    }

    /**
     * Creates the target database if it does not already exist.
     */
    private void ensureDatabaseExists(ServerRuntimeConfig runtimeConfig) {
        try (Connection connection = this.openConnection(
                runtimeConfig.adminJdbcUrl(),
                runtimeConfig.username(),
                runtimeConfig.password()
        );
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM pg_database WHERE datname = ?"
             )) {
            connection.setAutoCommit(true);
            statement.setString(1, runtimeConfig.databaseName());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }

            try (Statement createDatabase = connection.createStatement()) {
                createDatabase.execute("CREATE DATABASE " + this.quoteIdentifier(runtimeConfig.databaseName()));
            }
        } catch (SQLException exception) {
            PostgresDatabaseHealthException.Reason reason = this.isConnectionFailure(exception)
                    ? PostgresDatabaseHealthException.Reason.UNREACHABLE
                    : PostgresDatabaseHealthException.Reason.UNHEALTHY;

            String message = reason == PostgresDatabaseHealthException.Reason.UNREACHABLE
                    ? "PostgreSQL admin database is not reachable: " + exception.getMessage()
                    : "PostgreSQL is reachable but the target database cannot be created: " + exception.getMessage();

            throw new PostgresDatabaseHealthException(reason, message, exception);
        }
    }

    /**
     * Verifies that the target database accepts queries.
     */
    private void verifyReachability(PostgresConnectionConfig connectionConfig) {
        try (Connection connection = this.openConnection(
                connectionConfig.jdbcUrl(),
                connectionConfig.username(),
                connectionConfig.password()
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            if (!resultSet.next() || resultSet.getInt(1) != 1) {
                throw new PostgresDatabaseHealthException(
                        PostgresDatabaseHealthException.Reason.UNHEALTHY,
                        "PostgreSQL responded but health probe returned an unexpected result"
                );
            }
        } catch (SQLException exception) {
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNREACHABLE,
                    "PostgreSQL target database is not reachable: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Executes all DDL statements contributed by registered schema modules.
     */
    private void initializeSchema(PostgresConnectionConfig connectionConfig, List<PostgresSchemaModule> schemaModules) {
        try (Connection connection = this.openConnection(
                connectionConfig.jdbcUrl(),
                connectionConfig.username(),
                connectionConfig.password()
        );
             Statement statement = connection.createStatement()) {
            for (PostgresSchemaModule schemaModule : schemaModules) {
                for (String ddl : schemaModule.createStatements()) {
                    statement.execute(ddl);
                }
            }
        } catch (SQLException exception) {
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNHEALTHY,
                    "PostgreSQL schema initialization failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Verifies that every required table declared by the registered modules exists.
     */
    private void verifySchemaHealth(PostgresConnectionConfig connectionConfig, List<PostgresSchemaModule> schemaModules) {
        Set<String> missingTables = new LinkedHashSet<>();

        try (Connection connection = this.openConnection(
                connectionConfig.jdbcUrl(),
                connectionConfig.username(),
                connectionConfig.password()
        );
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1
                     FROM information_schema.tables
                     WHERE table_schema = current_schema()
                     AND table_name = ?
                     """)) {
            for (PostgresSchemaModule schemaModule : schemaModules) {
                for (String table : schemaModule.requiredTables()) {
                    statement.setString(1, table);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            missingTables.add(table);
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNHEALTHY,
                    "PostgreSQL schema verification failed: " + exception.getMessage(),
                    exception
            );
        }

        if (!missingTables.isEmpty()) {
            throw new PostgresDatabaseHealthException(
                    PostgresDatabaseHealthException.Reason.UNHEALTHY,
                    "PostgreSQL schema is not healthy. Missing tables: " + missingTables
            );
        }
    }

    /**
     * Opens a JDBC connection with the provided settings.
     */
    private Connection openConnection(String jdbcUrl, String username, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * Returns true when the SQL state indicates that the server cannot be contacted.
     */
    private boolean isConnectionFailure(SQLException exception) {
        String sqlState = exception.getSQLState();
        return sqlState == null || sqlState.startsWith("08");
    }

    /**
     * Quotes a PostgreSQL identifier safely.
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * Builds the cache key used to avoid repeated bootstrap on the same target and module set.
     */
    private String buildCacheKey(ServerRuntimeConfig runtimeConfig, List<PostgresSchemaModule> schemaModules) {
        String modulesFingerprint = schemaModules.stream()
                .map(PostgresSchemaModule::moduleName)
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("");

        return runtimeConfig.jdbcUrl() + "|" + modulesFingerprint;
    }

    /**
     * Singleton holder idiom.
     */
    private static final class Holder {
        private static final PostgresDatabaseBootstrap INSTANCE = new PostgresDatabaseBootstrap();

        /**
         * Prevents instantiation of the holder utility class.
         */
        private Holder() {
        }
    }
}
