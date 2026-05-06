package it.polimi.ingsoft.mesos.server.model.ranking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Encapsulates MySQL connection setup, schema initialization and transient-retry execution.
 */
final class MySqlDatabaseSupport {
    private static final List<String> TABLE_DDLS = List.of(
            MySqlRankingQueries.CREATE_USERS_TABLE,
            MySqlRankingQueries.CREATE_USER_RATINGS_TABLE,
            MySqlRankingQueries.CREATE_MATCHES_TABLE,
            MySqlRankingQueries.CREATE_MATCH_RESULTS_TABLE
    );

    private final MySqlConnectionConfig connectionConfig;
    private final int maxBusyRetries;

    /**
     * Creates MySQL support with connection settings and retry limit.
     *
     * @param connectionConfig MySQL connection settings
     * @param maxBusyRetries maximum retries for transient MySQL failures
     */
    MySqlDatabaseSupport(MySqlConnectionConfig connectionConfig,
                         int maxBusyRetries) {
        this.connectionConfig = Objects.requireNonNull(connectionConfig, "Connection config cannot be null");

        if (maxBusyRetries < 0) {
            throw new IllegalArgumentException("Max busy retries cannot be negative");
        }

        this.maxBusyRetries = maxBusyRetries;
    }

    /**
     * Creates all tables and indexes if missing.
     */
    void initializeSchema() {
        this.executeWithRetry(() -> {
            try (Connection connection = this.openConnection();
                 Statement statement = connection.createStatement()) {
                for (String ddl : TABLE_DDLS) {
                    statement.execute(ddl);
                }
                return null;
            }
        });
    }

    /**
     * Opens a MySQL connection configured for transactional operations.
     *
     * @return opened MySQL connection
     * @throws SQLException if connection fails
     */
    Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                this.connectionConfig.jdbcUrl(),
                this.connectionConfig.username(),
                this.connectionConfig.password()
        );
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        return connection;
    }

    /**
     * Executes an action retrying on transient MySQL failures.
     *
     * @param action operation to execute
     * @param <T> operation result type
     * @return action result
     */
    <T> T executeWithRetry(Callable<T> action) {
        int attempt = 0;

        while (true) {
            try {
                return action.call();
            } catch (SQLException exception) {
                if (attempt < this.maxBusyRetries && this.isRetryableException(exception)) {
                    attempt++;
                    this.sleepBeforeRetry(attempt);
                    continue;
                }

                throw new IllegalStateException("Database operation failed", exception);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Unexpected operation failure", exception);
            }
        }
    }

    /**
     * Detects transient MySQL failures that are worth retrying.
     */
    private boolean isRetryableException(SQLException exception) {
        String sqlState = exception.getSQLState();
        int errorCode = exception.getErrorCode();

        return "40001".equals(sqlState)
                || "41000".equals(sqlState)
                || errorCode == 1205
                || errorCode == 1213;
    }

    /**
     * Applies incremental backoff before the next retry attempt.
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted during database retry", exception);
        }
    }
}
