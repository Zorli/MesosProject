package it.polimi.ingsoft.mesos.server.persistence.postgres;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingSchemaModule;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresDatabaseBootstrapTest extends PostgresIntegrationTestSupport {

    @Test
    void ensureReadyCreatesMissingDatabaseAndRequiredTables() throws Exception {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("bootstrap_create", false);

        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresRankingSchemaModule.getInstance())
        );

        try (Connection connection = DriverManager.getConnection(
                runtimeConfig.jdbcUrl(),
                runtimeConfig.username(),
                runtimeConfig.password()
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM information_schema.tables
                     WHERE table_schema = current_schema()
                     AND table_name IN ('users', 'user_ratings', 'match_results')
                     """)) {
            assertTrue(resultSet.next());
            assertEquals(3, resultSet.getInt(1));
        }
    }

    @Test
    void ensureReadyThrowsWhenDatabaseIsNotReachable() {
        ServerRuntimeConfig runtimeConfig = new ServerRuntimeConfig(
                "jdbc:postgresql://localhost:1/mesos",
                "jdbc:postgresql://localhost:1/postgres",
                "postgres",
                "postgres",
                0
        );

        PostgresDatabaseHealthException exception = assertThrows(
                PostgresDatabaseHealthException.class,
                () -> PostgresDatabaseBootstrap.getInstance().ensureReady(
                        runtimeConfig,
                        List.of(PostgresRankingSchemaModule.getInstance())
                )
        );

        assertEquals(PostgresDatabaseHealthException.Reason.UNREACHABLE, exception.reason());
    }

    @Test
    void ensureReadyThrowsWhenSchemaIsUnhealthy() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("bootstrap_unhealthy", true);
        PostgresSchemaModule brokenModule = new PostgresSchemaModule() {
            @Override
            public String moduleName() {
                return "broken";
            }

            @Override
            public List<String> createStatements() {
                return List.of();
            }

            @Override
            public List<String> requiredTables() {
                return List.of("missing_table");
            }
        };

        PostgresDatabaseHealthException exception = assertThrows(
                PostgresDatabaseHealthException.class,
                () -> PostgresDatabaseBootstrap.getInstance().ensureReady(runtimeConfig, List.of(brokenModule))
        );

        assertEquals(PostgresDatabaseHealthException.Reason.UNHEALTHY, exception.reason());
        assertInstanceOf(PostgresDatabaseHealthException.class, exception);
        assertTrue(exception.getMessage().contains("missing_table"));
    }
}
