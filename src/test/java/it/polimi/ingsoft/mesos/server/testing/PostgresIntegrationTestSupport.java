package it.polimi.ingsoft.mesos.server.testing;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Shared PostgreSQL container and isolated test-database lifecycle for integration tests.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTestSupport {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mesos")
            .withUsername("postgres")
            .withPassword("postgres");

    private final List<String> createdDatabaseNames = new ArrayList<>();

    /**
     * Creates an isolated PostgreSQL database and returns a connection config bound to it.
     *
     * @param prefix database name prefix used for easier diagnostics
     * @return PostgreSQL connection settings targeting the isolated database
     */
    protected PostgresConnectionConfig createIsolatedConnectionConfig(String prefix) {
        String databaseName = this.reserveDatabaseName(prefix);
        this.createDatabase(databaseName);
        return new PostgresConnectionConfig(this.buildJdbcUrl(databaseName), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /**
     * Creates runtime config for an isolated PostgreSQL database.
     *
     * @param prefix database name prefix used for easier diagnostics
     * @param createDatabase whether the database must be created immediately
     * @return server runtime config targeting the isolated database
     */
    protected ServerRuntimeConfig createRuntimeConfig(String prefix, boolean createDatabase) {
        String databaseName = this.reserveDatabaseName(prefix);
        if (createDatabase) {
            this.createDatabase(databaseName);
        }

        return new ServerRuntimeConfig(
                this.buildJdbcUrl(databaseName),
                this.adminJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword(),
                3
        );
    }

    /**
     * Drops every test database created during the current test method.
     */
    @AfterEach
    void cleanupCreatedDatabases() {
        for (String databaseName : this.createdDatabaseNames) {
            try (Connection connection = DriverManager.getConnection(
                    this.adminJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword()
            );
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + this.quoteIdentifier(databaseName) + " WITH (FORCE)");
            } catch (SQLException exception) {
                throw new IllegalStateException("Cannot drop isolated PostgreSQL test database: " + databaseName, exception);
            }
        }

        this.createdDatabaseNames.clear();
    }

    /**
     * Creates one PostgreSQL database for the current test.
     */
    private void createDatabase(String databaseName) {
        try (Connection connection = DriverManager.getConnection(
                this.adminJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + this.quoteIdentifier(databaseName));
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create isolated PostgreSQL test database", exception);
        }
    }

    /**
     * Reserves a unique database name and registers it for cleanup.
     */
    private String reserveDatabaseName(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "ranking"
                : prefix.toLowerCase(Locale.ROOT).replace('-', '_');

        String databaseName = normalizedPrefix + "_" + UUID.randomUUID().toString().replace("-", "");
        this.createdDatabaseNames.add(databaseName);
        return databaseName;
    }

    /**
     * Builds the JDBC URL for a specific database inside the shared PostgreSQL container.
     */
    private String buildJdbcUrl(String databaseName) {
        return "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/"
                + databaseName;
    }

    /**
     * Returns the JDBC URL of the PostgreSQL admin database used to create and drop test databases.
     */
    private String adminJdbcUrl() {
        return this.buildJdbcUrl("postgres");
    }

    /**
     * Quotes a PostgreSQL identifier safely.
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
