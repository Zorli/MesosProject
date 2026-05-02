package it.polimi.ingsoft.mesos.server.model.persistence.mysql;

import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.ranking.MySqlConnectionConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton responsible for bootstrapping MySQL and verifying schema health at application startup.
 */
public final class MySqlDatabaseBootstrap {
    private final Set<String> initializedTargets;

    /**
     * Creates the singleton bootstrap and initializes the per-target bootstrap cache.
     */
    private MySqlDatabaseBootstrap() {
        this.initializedTargets = ConcurrentHashMap.newKeySet();
    }

    /**
     * Returns the shared singleton instance.
     *
     * @return singleton bootstrap instance
     */
    public static MySqlDatabaseBootstrap getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Ensures the target database exists, is reachable, and contains every required table.
     *
     * @param runtimeConfig runtime configuration
     * @param schemaModules modular schema definitions to initialize and verify
     */
    public void ensureReady(ServerRuntimeConfig runtimeConfig, List<MySqlSchemaModule> schemaModules) {
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
            this.verifyReachability(runtimeConfig.toMySqlConnectionConfig());
            this.initializeSchema(runtimeConfig.toMySqlConnectionConfig(), schemaModules);
            this.verifySchemaHealth(runtimeConfig.toMySqlConnectionConfig(), schemaModules);

            this.initializedTargets.add(cacheKey);
        }
    }

    /**
     * Validates that at least one schema module has been provided.
     */
    private void validateSchemaModules(List<MySqlSchemaModule> schemaModules) {
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
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute(
                    "CREATE DATABASE IF NOT EXISTS "
                            + this.quoteIdentifier(runtimeConfig.databaseName())
                            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"
            );
        } catch (SQLException exception) {
            MySqlDatabaseHealthException.Reason reason = this.isConnectionFailure(exception)
                    ? MySqlDatabaseHealthException.Reason.UNREACHABLE
                    : MySqlDatabaseHealthException.Reason.UNHEALTHY;

            String message = reason == MySqlDatabaseHealthException.Reason.UNREACHABLE
                    ? "MySQL admin database is not reachable: " + exception.getMessage()
                    : "MySQL is reachable but the target database cannot be created: " + exception.getMessage();

            throw new MySqlDatabaseHealthException(reason, message, exception);
        }
    }

    /**
     * Verifies that the target database accepts queries.
     */
    private void verifyReachability(MySqlConnectionConfig connectionConfig) {
        try (Connection connection = this.openConnection(
                connectionConfig.jdbcUrl(),
                connectionConfig.username(),
                connectionConfig.password()
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            if (!resultSet.next() || resultSet.getInt(1) != 1) {
                throw new MySqlDatabaseHealthException(
                        MySqlDatabaseHealthException.Reason.UNHEALTHY,
                        "MySQL responded but health probe returned an unexpected result"
                );
            }
        } catch (SQLException exception) {
            throw new MySqlDatabaseHealthException(
                    MySqlDatabaseHealthException.Reason.UNREACHABLE,
                    "MySQL target database is not reachable: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Executes all DDL statements contributed by registered schema modules.
     */
    private void initializeSchema(MySqlConnectionConfig connectionConfig, List<MySqlSchemaModule> schemaModules) {
        try (Connection connection = this.openConnection(
                connectionConfig.jdbcUrl(),
                connectionConfig.username(),
                connectionConfig.password()
        );
             Statement statement = connection.createStatement()) {
            for (MySqlSchemaModule schemaModule : schemaModules) {
                for (String ddl : schemaModule.createStatements()) {
                    statement.execute(ddl);
                }
            }
        } catch (SQLException exception) {
            throw new MySqlDatabaseHealthException(
                    MySqlDatabaseHealthException.Reason.UNHEALTHY,
                    "MySQL schema initialization failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Verifies that every required table declared by the registered modules exists.
     */
    private void verifySchemaHealth(MySqlConnectionConfig connectionConfig, List<MySqlSchemaModule> schemaModules) {
        Set<String> missingTables = new LinkedHashSet<>();

        try (Connection connection = this.openConnection(
                connectionConfig.jdbcUrl(),
                connectionConfig.username(),
                connectionConfig.password()
        );
             java.sql.PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1
                     FROM information_schema.tables
                     WHERE table_schema = DATABASE()
                     AND table_name = ?
                     """)) {
            for (MySqlSchemaModule schemaModule : schemaModules) {
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
            throw new MySqlDatabaseHealthException(
                    MySqlDatabaseHealthException.Reason.UNHEALTHY,
                    "MySQL schema verification failed: " + exception.getMessage(),
                    exception
            );
        }

        if (!missingTables.isEmpty()) {
            throw new MySqlDatabaseHealthException(
                    MySqlDatabaseHealthException.Reason.UNHEALTHY,
                    "MySQL schema is not healthy. Missing tables: " + missingTables
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
     * Quotes a MySQL identifier safely.
     */
    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * Builds the cache key used to avoid repeated bootstrap on the same target and module set.
     */
    private String buildCacheKey(ServerRuntimeConfig runtimeConfig, List<MySqlSchemaModule> schemaModules) {
        String modulesFingerprint = schemaModules.stream()
                .map(MySqlSchemaModule::moduleName)
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("");

        return runtimeConfig.jdbcUrl() + "|" + runtimeConfig.username() + "|" + modulesFingerprint;
    }

    /**
     * Singleton holder idiom.
     */
    private static final class Holder {
        private static final MySqlDatabaseBootstrap INSTANCE = new MySqlDatabaseBootstrap();

        /**
         * Prevents instantiation of the holder utility class.
         */
        private Holder() {
        }
    }
}
