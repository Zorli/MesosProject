package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.testing.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class MySqlDatabaseSupportTest extends MySqlIntegrationTestSupport {
    private static final long SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE;

    @Test
    void constructorRejectsInvalidArguments() {
        NullPointerException nullPointerException = assertThrows(
                NullPointerException.class,
                () -> new MySqlDatabaseSupport(null, 0)
        );
        assertEquals("Connection config cannot be null", nullPointerException.getMessage());

        IllegalArgumentException illegalArgumentException = assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlDatabaseSupport(this.createIsolatedConnectionConfig("invalid"), -1)
        );
        assertEquals("Max busy retries cannot be negative", illegalArgumentException.getMessage());
    }

    @Test
    void initializeSchemaCreatesTables() throws SQLException {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("database_support"),
                0
        );

        support.initializeSchema();

        try (Connection connection = support.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) " +
                             "FROM information_schema.tables " +
                             "WHERE table_schema = DATABASE() " +
                             "AND table_name IN ('users', 'user_ratings', 'matches', 'match_results')"
             )) {
            assertTrue(resultSet.next());
            assertEquals(4, resultSet.getInt(1));
        }
    }

    @Test
    void initializeSchemaIsIdempotent() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("schema_idempotent"),
                0
        );

        support.initializeSchema();
        support.initializeSchema();
    }

    @Test
    void executeWithRetryRetriesSqlState41000BeforeSuccess() throws SQLException {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_sqlstate_41000"),
                3
        );
        AtomicInteger attempts = new AtomicInteger();

        String result = support.executeWithRetry(() -> {
            if (attempts.getAndIncrement() < 2) {
                throw new SQLException("lock wait", "41000");
            }

            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void executeWithRetryRetriesMysqlErrorCode1205BeforeSuccess() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_error_code_1205"),
                3
        );
        AtomicInteger attempts = new AtomicInteger();

        String result = support.executeWithRetry(() -> {
            if (attempts.getAndIncrement() < 1) {
                throw new SQLException("lock wait", "99999", 1205);
            }

            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void executeWithRetryDoesNotRetryOnNonRetryableSqlException() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_nonretryable"),
                3
        );
        AtomicInteger attempts = new AtomicInteger();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                support.executeWithRetry(() -> {
                    attempts.getAndIncrement();
                    throw new SQLException("syntax", "42000");
                })
        );

        assertEquals("Database operation failed", exception.getMessage());
        assertEquals(1, attempts.get());
    }

    @Test
    void openConnectionUsesSerializableIsolationLevel() throws SQLException {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("connection_isolation"),
                0
        );

        try (Connection connection = support.openConnection()) {
            assertEquals(SERIALIZABLE, connection.getTransactionIsolation());
        }
    }

    @Test
    void executeWithRetryRetriesBusyErrorsBeforeSuccess() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_success"),
                3
        );
        AtomicInteger attempts = new AtomicInteger();

        String result = support.executeWithRetry(() -> {
            if (attempts.getAndIncrement() < 2) {
                throw new SQLException("serialization failure", "40001");
            }

            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void executeWithRetryRetriesMySqlDeadlocksBeforeSuccess() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_deadlock"),
                3
        );
        AtomicInteger attempts = new AtomicInteger();

        String result = support.executeWithRetry(() -> {
            if (attempts.getAndIncrement() < 1) {
                throw new SQLException("deadlock", "40001", 1213);
            }

            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void executeWithRetryThrowsWhenBusyRetriesAreExhausted() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_exhausted"),
                1
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                support.executeWithRetry(() -> {
                    throw new SQLException("serialization failure", "40001");
                })
        );

        assertEquals("Database operation failed", exception.getMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void executeWithRetryInterruptedDuringRetrySleep() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_interrupt"),
                3
        );

        try {
            Thread.currentThread().interrupt();

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                    support.executeWithRetry(() -> {
                        throw new SQLException("serialization failure", "40001");
                    })
            );

            assertEquals("Thread interrupted during database retry", exception.getMessage());
            assertInstanceOf(InterruptedException.class, exception.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void executeWithRetryPropagatesIllegalStateExceptionAsIs() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_illegal_state"),
                0
        );

        IllegalStateException expected = new IllegalStateException("fatal");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                support.executeWithRetry(() -> {
                    throw expected;
                })
        );

        assertEquals(expected, thrown);
    }

    @Test
    void executeWithRetryWrapsUnexpectedExceptions() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_unexpected"),
                0
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                support.executeWithRetry(() -> {
                    throw new Exception("boom");
                })
        );

        assertEquals("Unexpected operation failure", exception.getMessage());
        assertInstanceOf(Exception.class, exception.getCause());
    }

    @Test
    void executeWithRetryPropagatesRuntimeExceptionsAsIs() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_runtime"),
                0
        );

        RuntimeException runtimeException = assertThrows(RuntimeException.class, () ->
                support.executeWithRetry(() -> {
                    throw new IllegalStateException("runtime");
                })
        );

        assertEquals("runtime", runtimeException.getMessage());
    }
}
