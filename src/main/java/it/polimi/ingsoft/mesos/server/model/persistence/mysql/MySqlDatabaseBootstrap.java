package it.polimi.ingsoft.mesos.server.model.persistence.mysql;

import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.ranking.MySqlConnectionConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Minimal MySQL bootstrap for ranking persistence.
 */
public final class MySqlDatabaseBootstrap {
    private MySqlDatabaseBootstrap() {
    }

    /**
     * Ensures the target database exists and is reachable.
     *
     * @param runtimeConfig runtime configuration
     */
    public static void ensureReady(ServerRuntimeConfig runtimeConfig) {
        Objects.requireNonNull(runtimeConfig, "Runtime config cannot be null");

        ensureDatabaseExists(runtimeConfig);
        verifyReachability(runtimeConfig.toMySqlConnectionConfig());
    }

    /**
     * Creates the target database if it does not already exist.
     */
    private static void ensureDatabaseExists(ServerRuntimeConfig runtimeConfig) {
        try (Connection connection = openConnection(
                runtimeConfig.adminJdbcUrl(),
                runtimeConfig.username(),
                runtimeConfig.password()
        );
            Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute(
                    "CREATE DATABASE IF NOT EXISTS "
                            + quoteIdentifier(runtimeConfig.databaseName())
                            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"
            );
        } catch (SQLException exception) {
            String message = isConnectionFailure(exception)
                    ? "MySQL admin database is not reachable: " + exception.getMessage()
                    : "MySQL is reachable but the target database cannot be created: " + exception.getMessage();

            throw new IllegalStateException(message, exception);
        }
    }

    /**
     * Verifies that the target database accepts queries.
     */
    private static void verifyReachability(MySqlConnectionConfig connectionConfig) {
        try (Connection connection = openConnection(
                connectionConfig.jdbcUrl(),
                connectionConfig.username(),
                connectionConfig.password()
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            if (!resultSet.next() || resultSet.getInt(1) != 1) {
                throw new IllegalStateException("MySQL responded but health probe returned an unexpected result");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "MySQL target database is not reachable: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Opens a JDBC connection with the provided settings.
     */
    private static Connection openConnection(String jdbcUrl, String username, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * Returns true when the SQL state indicates that the server cannot be contacted.
     */
    private static boolean isConnectionFailure(SQLException exception) {
        String sqlState = exception.getSQLState();
        return sqlState == null || sqlState.startsWith("08");
    }

    /**
     * Quotes a MySQL identifier safely.
     */
    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
