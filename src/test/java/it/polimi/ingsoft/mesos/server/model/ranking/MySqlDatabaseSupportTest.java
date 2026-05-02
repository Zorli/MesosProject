package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.MySqlDatabaseSupport;
import it.polimi.ingsoft.mesos.server.model.ranking.MySqlRankingSchemaModule;
import it.polimi.ingsoft.mesos.server.testing.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class MySqlDatabaseSupportTest extends MySqlIntegrationTestSupport {

    @Test
    void constructorRejectsInvalidArguments() {
        NullPointerException nullPointerException = assertThrows(
                NullPointerException.class,
                () -> new MySqlDatabaseSupport(null, 0, List.of(MySqlRankingSchemaModule.getInstance()))
        );
        assertEquals("Connection config cannot be null", nullPointerException.getMessage());

        IllegalArgumentException illegalArgumentException = assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlDatabaseSupport(
                        this.createIsolatedConnectionConfig("invalid"),
                        -1,
                        List.of(MySqlRankingSchemaModule.getInstance())
                )
        );
        assertEquals("Max busy retries cannot be negative", illegalArgumentException.getMessage());

        IllegalArgumentException modulesException = assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlDatabaseSupport(this.createIsolatedConnectionConfig("missing_modules"), 0, List.of())
        );
        assertEquals("Schema modules cannot be null or empty", modulesException.getMessage());
    }

    @Test
    void initializeSchemaCreatesTables() throws SQLException {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("database_support"),
                0,
                List.of(MySqlRankingSchemaModule.getInstance())
        );

        support.initializeSchema();

        try (Connection connection = support.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) " +
                             "FROM information_schema.tables " +
                             "WHERE table_schema = DATABASE() " +
                             "AND table_name IN ('users', 'user_ratings', 'match_results')"
             )) {
            assertTrue(resultSet.next());
            assertEquals(3, resultSet.getInt(1));
        }
    }

    @Test
    void executeWithRetryRetriesBusyErrorsBeforeSuccess() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_success"),
                3,
                List.of(MySqlRankingSchemaModule.getInstance())
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
                3,
                List.of(MySqlRankingSchemaModule.getInstance())
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
                1,
                List.of(MySqlRankingSchemaModule.getInstance())
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
    void executeWithRetryPropagatesIllegalStateExceptionAsIs() {
        MySqlDatabaseSupport support = new MySqlDatabaseSupport(
                this.createIsolatedConnectionConfig("retry_illegal_state"),
                0,
                List.of(MySqlRankingSchemaModule.getInstance())
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
                0,
                List.of(MySqlRankingSchemaModule.getInstance())
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                support.executeWithRetry(() -> {
                    throw new RuntimeException("boom");
                })
        );

        assertEquals("Unexpected operation failure", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
    }
}
