package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDatabaseSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void constructorRejectsInvalidArguments() {
        NullPointerException nullPointerException = assertThrows(
                NullPointerException.class,
                () -> new SqliteDatabaseSupport(null, 0)
        );
        assertEquals("Database path cannot be null", nullPointerException.getMessage());

        IllegalArgumentException illegalArgumentException = assertThrows(
                IllegalArgumentException.class,
                () -> new SqliteDatabaseSupport(this.tempDir.resolve("ranking.db"), -1)
        );
        assertEquals("Max busy retries cannot be negative", illegalArgumentException.getMessage());
    }

    @Test
    void initializeSchemaCreatesTables() throws SQLException {
        SqliteDatabaseSupport support = new SqliteDatabaseSupport(this.tempDir.resolve("ranking.db"), 0);

        support.initializeSchema();

        try (Connection connection = support.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('users', 'user_ratings', 'match_results')"
             )) {
            assertTrue(resultSet.next());
            assertEquals(3, resultSet.getInt(1));
        }
    }

    @Test
    void executeWithRetryRetriesBusyErrorsBeforeSuccess() {
        SqliteDatabaseSupport support = new SqliteDatabaseSupport(this.tempDir.resolve("ranking.db"), 3);
        AtomicInteger attempts = new AtomicInteger();

        String result = support.executeWithRetry(() -> {
            if (attempts.getAndIncrement() < 2) {
                throw new SQLException("database is locked", "SQLITE_BUSY", 5);
            }

            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void executeWithRetryThrowsWhenBusyRetriesAreExhausted() {
        SqliteDatabaseSupport support = new SqliteDatabaseSupport(this.tempDir.resolve("ranking.db"), 1);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                support.executeWithRetry(() -> {
                    throw new SQLException("database is locked", "SQLITE_BUSY", 5);
                })
        );

        assertEquals("Database operation failed", exception.getMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void executeWithRetryPropagatesIllegalStateExceptionAsIs() {
        SqliteDatabaseSupport support = new SqliteDatabaseSupport(this.tempDir.resolve("ranking.db"), 0);

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
        SqliteDatabaseSupport support = new SqliteDatabaseSupport(this.tempDir.resolve("ranking.db"), 0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                support.executeWithRetry(() -> {
                    throw new RuntimeException("boom");
                })
        );

        assertEquals("Unexpected operation failure", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
    }
}
