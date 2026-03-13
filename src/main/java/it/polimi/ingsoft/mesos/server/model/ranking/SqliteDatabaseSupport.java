package it.polimi.ingsoft.mesos.server.model.ranking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Encapsulates SQLite connection setup, schema initialization and busy-retry execution.
 */
final class SqliteDatabaseSupport {
    private final String jdbcUrl;
    private final int maxBusyRetries;

    /**
     * Creates SQLite support with a database file path and retry limit.
     *
     * @param databasePath path of the SQLite database file
     * @param maxBusyRetries maximum retries for busy/locked SQLite operations
     */
    SqliteDatabaseSupport(Path databasePath, int maxBusyRetries) {
        Objects.requireNonNull(databasePath, "Database path cannot be null");

        if (maxBusyRetries < 0) {
            throw new IllegalArgumentException("Max busy retries cannot be negative");
        }

        Path absolutePath = databasePath.toAbsolutePath();

        try {
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create directories for database path", exception);
        }

        this.jdbcUrl = "jdbc:sqlite:" + absolutePath;
        this.maxBusyRetries = maxBusyRetries;
    }

    /**
     * Creates all tables and indexes if missing.
     */
    void initializeSchema() {
        this.executeWithRetry(() -> {
            try (Connection connection = this.openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(SqliteRankingQueries.CREATE_USERS_TABLE);
                statement.execute(SqliteRankingQueries.CREATE_USER_RATINGS_TABLE);
                statement.execute(SqliteRankingQueries.CREATE_MATCH_RESULTS_TABLE);
                statement.execute(SqliteRankingQueries.CREATE_RATINGS_INDEX);
                statement.execute(SqliteRankingQueries.CREATE_MATCH_HISTORY_INDEX);
                statement.execute(SqliteRankingQueries.CREATE_MATCH_PLAYER_COUNT_INDEX);
                return null;
            }
        });
    }

    /**
     * Opens a connection configured for transactional and concurrent-safe usage.
     *
     * @return opened SQLite connection
     * @throws SQLException if connection or pragma setup fails
     */
    Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(this.jdbcUrl);
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }

        return connection;
    }

    /**
     * Executes an action retrying on SQLite busy/locked errors.
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
                if (this.isBusyException(exception) && attempt < this.maxBusyRetries) {
                    attempt++;
                    this.sleepBeforeRetry(attempt);
                    continue;
                }

                throw new IllegalStateException("Database operation failed", exception);
            } catch (Exception exception) {
                if (exception instanceof IllegalStateException illegalStateException) {
                    throw illegalStateException;
                }

                throw new IllegalStateException("Unexpected operation failure", exception);
            }
        }
    }

    /**
     * Detects SQLite busy/locked failures that can be retried.
     */
    private boolean isBusyException(SQLException exception) {
        String message = exception.getMessage();

        return exception.getErrorCode() == 5
                || exception.getErrorCode() == 6
                || (message != null && message.toLowerCase().contains("database is locked"));
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
