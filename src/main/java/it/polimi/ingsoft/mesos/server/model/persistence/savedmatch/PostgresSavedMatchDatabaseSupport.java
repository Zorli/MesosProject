package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;
import it.polimi.ingsoft.mesos.server.model.persistence.postgres.PostgresSchemaModule;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Encapsulates PostgreSQL connection setup, schema initialization and transient-retry execution
 * for saved matches.
 */
final class PostgresSavedMatchDatabaseSupport {
    private final PostgresConnectionConfig connectionConfig;
    private final int maxBusyRetries;
    private final List<PostgresSchemaModule> schemaModules;

    /**
     * Creates PostgreSQL support with connection settings and retry limit.
     *
     * @param connectionConfig PostgreSQL connection settings
     * @param maxBusyRetries maximum retries for transient PostgreSQL failures
     * @param schemaModules schema modules to initialize
     */
    PostgresSavedMatchDatabaseSupport(PostgresConnectionConfig connectionConfig,
                                      int maxBusyRetries,
                                      List<PostgresSchemaModule> schemaModules) {
        this.connectionConfig = Objects.requireNonNull(connectionConfig, "Connection config cannot be null");
        if (maxBusyRetries < 0) {
            throw new IllegalArgumentException("Max busy retries cannot be negative");
        }
        if (schemaModules == null || schemaModules.isEmpty()) {
            throw new IllegalArgumentException("Schema modules cannot be null or empty");
        }

        this.maxBusyRetries = maxBusyRetries;
        this.schemaModules = List.copyOf(schemaModules);
    }

    /**
     * Creates all tables and indexes if missing.
     */
    void initializeSchema() {
        this.executeWithRetry(() -> {
            try (Connection connection = this.openConnection();
                 Statement statement = connection.createStatement()) {
                for (PostgresSchemaModule schemaModule : this.schemaModules) {
                    for (String ddl : schemaModule.createStatements()) {
                        statement.execute(ddl);
                    }
                }
                return null;
            }
        });
    }

    /**
     * Opens a PostgreSQL connection configured for transactional operations.
     *
     * @return opened PostgreSQL connection
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
     * Executes an action retrying on transient PostgreSQL failures.
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
                if (this.isRetryableException(exception) && attempt < this.maxBusyRetries) {
                    attempt++;
                    this.sleepBeforeRetry(attempt);
                    continue;
                }

                throw new IllegalStateException("Saved-match database operation failed", exception);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Unexpected saved-match operation failure", exception);
            }
        }
    }

    /**
     * Returns whether the SQL state identifies a retryable transactional failure.
     *
     * @param exception SQL failure raised by PostgreSQL
     * @return {@code true} for serialization, deadlock, or lock-not-available failures
     */
    private boolean isRetryableException(SQLException exception) {
        String sqlState = exception.getSQLState();
        return "40001".equals(sqlState)
                || "40P01".equals(sqlState)
                || "55P03".equals(sqlState);
    }

    /**
     * Sleeps for a short backoff interval before the next retry attempt.
     *
     * @param attempt current retry attempt number starting from one
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
