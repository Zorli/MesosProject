package it.polimi.ingsoft.mesos.server.testing;

import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**

 * Shared PostgreSQL backend and isolated test-database lifecycle for integration tests.
 *
 * <p>The support prefers an already reachable PostgreSQL instance configured via
 * {@code MESOS_DB_*} environment variables or the local development defaults
 * ({@code localhost:5432}, {@code postgres}/{@code postgres}). When no external backend is
 * reachable, it falls back to a transient Testcontainers PostgreSQL instance. This keeps
 * integration tests stable in local environments where the repository already provides PostgreSQL
 * through {@code docker-compose.yml}, while still supporting clean ephemeral execution in CI.

 */
public abstract class PostgresIntegrationTestSupport {
    private static final int POSTGRES_IDENTIFIER_MAX_LENGTH = 63;
    private static final int RANDOM_SUFFIX_LENGTH = 32;
    private static final PostgresTestBackend BACKEND = PostgresTestBackend.detect();

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
        return new PostgresConnectionConfig(this.buildJdbcUrl(databaseName), BACKEND.username(), BACKEND.password());
    }

    /**
     * Drops every test database created during the current test method.
     */
    @AfterEach
    void cleanupCreatedDatabases() {
        for (String databaseName : this.createdDatabaseNames) {
            try (Connection connection = DriverManager.getConnection(
                    this.adminJdbcUrl(),
                    BACKEND.username(),
                    BACKEND.password()
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
                BACKEND.username(),
                BACKEND.password()
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
        int maxPrefixLength = POSTGRES_IDENTIFIER_MAX_LENGTH - RANDOM_SUFFIX_LENGTH - 1;
        if (normalizedPrefix.length() > maxPrefixLength) {
            normalizedPrefix = normalizedPrefix.substring(0, maxPrefixLength);
        }

        String databaseName = normalizedPrefix + "_" + UUID.randomUUID().toString().replace("-", "");
        this.createdDatabaseNames.add(databaseName);
        return databaseName;
    }

    /**
     * Builds the JDBC URL for a specific database inside the shared PostgreSQL container.
     */
    private String buildJdbcUrl(String databaseName) {
        return BACKEND.jdbcUrlForDatabase(databaseName);
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

    /**
     * Resolves the PostgreSQL backend used by integration tests.
     */
    private record PostgresTestBackend(String adminJdbcUrl,
                                       String defaultJdbcUrl,
                                       String username,
                                       String password) {
        private static final String BACKEND_MODE_PROPERTY = "mesos.test.postgres.backend";
        private static final String BACKEND_MODE_ENV = "MESOS_TEST_POSTGRES_BACKEND";
        private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:5432/mesos";
        private static final String DEFAULT_ADMIN_JDBC_URL = "jdbc:postgresql://localhost:5432/postgres";
        private static final String DEFAULT_USERNAME = "postgres";
        private static final String DEFAULT_PASSWORD = "postgres";

        private static PostgresTestBackend detect() {
            BackendMode backendMode = BackendMode.resolve();
            PostgresTestBackend externalBackend = fromEnvironment();
            RuntimeException containerFailure = null;

            if (backendMode != BackendMode.TESTCONTAINERS && externalBackend.isReachable()) {
                return externalBackend;
            }

            if (backendMode != BackendMode.EXTERNAL) {
                try {
                    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                            .withDatabaseName("mesos")
                            .withUsername(DEFAULT_USERNAME)
                            .withPassword(DEFAULT_PASSWORD);
                    postgres.start();
                    return new PostgresTestBackend(
                            jdbcUrlForDatabase(postgres.getJdbcUrl(), "postgres"),
                            postgres.getJdbcUrl(),
                            postgres.getUsername(),
                            postgres.getPassword()
                    );
                } catch (RuntimeException exception) {
                    containerFailure = exception;
                }
            }

            throw new IllegalStateException(
                    "PostgreSQL integration tests require either a working Testcontainers Docker environment "
                            + "or a reachable PostgreSQL instance configured via MESOS_DB_URL/MESOS_DB_ADMIN_URL "
                            + "(defaults: jdbc:postgresql://localhost:5432/mesos and /postgres).",
                    containerFailure
            );
        }

        private static PostgresTestBackend fromEnvironment() {
            return new PostgresTestBackend(
                    environmentValue("MESOS_DB_ADMIN_URL", DEFAULT_ADMIN_JDBC_URL),
                    environmentValue("MESOS_DB_URL", DEFAULT_JDBC_URL),
                    environmentValue("MESOS_DB_USER", DEFAULT_USERNAME),
                    environmentValue("MESOS_DB_PASSWORD", DEFAULT_PASSWORD)
            );
        }

        private static String environmentValue(String key, String defaultValue) {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? defaultValue : value;
        }

        private boolean isReachable() {
            try (Connection ignored = DriverManager.getConnection(this.adminJdbcUrl, this.username, this.password)) {
                return true;
            } catch (SQLException exception) {
                return false;
            }
        }

        private String jdbcUrlForDatabase(String databaseName) {
            return jdbcUrlForDatabase(this.defaultJdbcUrl, databaseName);
        }

        private static String jdbcUrlForDatabase(String templateJdbcUrl, String databaseName) {
            String prefix = "jdbc:postgresql://";
            if (templateJdbcUrl == null || !templateJdbcUrl.startsWith(prefix)) {
                throw new IllegalArgumentException("Unsupported JDBC url: " + templateJdbcUrl);
            }

            String remainder = templateJdbcUrl.substring(prefix.length());
            int slashIndex = remainder.indexOf('/');
            if (slashIndex < 0 || slashIndex == remainder.length() - 1) {
                throw new IllegalArgumentException("JDBC url must include a database name: " + templateJdbcUrl);
            }

            String hostAndPort = remainder.substring(0, slashIndex);
            String databaseAndQuery = remainder.substring(slashIndex + 1);
            int queryIndex = databaseAndQuery.indexOf('?');
            String querySuffix = queryIndex >= 0 ? databaseAndQuery.substring(queryIndex) : "";
            return prefix + hostAndPort + "/" + databaseName + querySuffix;
        }

        private enum BackendMode {
            AUTO,
            EXTERNAL,
            TESTCONTAINERS;

            private static BackendMode resolve() {
                String rawValue = System.getProperty(BACKEND_MODE_PROPERTY);
                if (rawValue == null || rawValue.isBlank()) {
                    rawValue = System.getenv(BACKEND_MODE_ENV);
                }

                if (rawValue == null || rawValue.isBlank()) {
                    return AUTO;
                }

                return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
                    case "auto" -> AUTO;
                    case "external", "local" -> EXTERNAL;
                    case "testcontainers", "container" -> TESTCONTAINERS;
                    default -> throw new IllegalArgumentException(
                            "Unsupported PostgreSQL test backend mode: " + rawValue
                    );
                };
            }
        }
    }
}
