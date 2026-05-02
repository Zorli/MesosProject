package it.polimi.ingsoft.mesos.server.testing;

import it.polimi.ingsoft.mesos.server.model.ranking.MySqlConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**

 * Shared MySQL backend and isolated test-database lifecycle for integration tests.
 *
 * <p>The support prefers an already reachable MySQL instance configured via
 * {@code MESOS_DB_*} environment variables or the local development defaults
 * ({@code localhost:3306}, {@code root}/{@code root}). When no external backend is
 * reachable, it falls back to a transient Testcontainers MySQL instance. This keeps
 * integration tests stable in local environments where the repository already provides MySQL
 * through {@code docker-compose.yml}, while still supporting clean ephemeral execution in CI.

 */
public abstract class MySqlIntegrationTestSupport {
    private static final int MYSQL_IDENTIFIER_MAX_LENGTH = 64;
    private static final int RANDOM_SUFFIX_LENGTH = 32;
    private static final MySqlTestBackend BACKEND = MySqlTestBackend.detect();

    private final List<String> createdDatabaseNames = new ArrayList<>();

    /**
     * Creates an isolated MySQL database and returns a connection config bound to it.
     *
     * @param prefix database name prefix used for easier diagnostics
     * @return MySQL connection settings targeting the isolated database
     */
    protected MySqlConnectionConfig createIsolatedConnectionConfig(String prefix) {
        String databaseName = this.reserveDatabaseName(prefix);
        this.createDatabase(databaseName);
        return new MySqlConnectionConfig(this.buildJdbcUrl(databaseName), BACKEND.username(), BACKEND.password());
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
                statement.execute("DROP DATABASE IF EXISTS " + this.quoteIdentifier(databaseName));
            } catch (SQLException exception) {
                throw new IllegalStateException("Cannot drop isolated MySQL test database: " + databaseName, exception);
            }
        }

        this.createdDatabaseNames.clear();
    }

    /**
     * Creates one MySQL database for the current test.
     */
    private void createDatabase(String databaseName) {
        try (Connection connection = DriverManager.getConnection(
                this.adminJdbcUrl(),
                BACKEND.username(),
                BACKEND.password()
            );
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE DATABASE "
                            + this.quoteIdentifier(databaseName)
                            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create isolated MySQL test database", exception);
        }
    }

    /**
     * Reserves a unique database name and registers it for cleanup.
     */
    private String reserveDatabaseName(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "ranking"
                : prefix.toLowerCase(Locale.ROOT).replace('-', '_');
        int maxPrefixLength = MYSQL_IDENTIFIER_MAX_LENGTH - RANDOM_SUFFIX_LENGTH - 1;
        if (normalizedPrefix.length() > maxPrefixLength) {
            normalizedPrefix = normalizedPrefix.substring(0, maxPrefixLength);
        }

        String databaseName = normalizedPrefix + "_" + UUID.randomUUID().toString().replace("-", "");
        this.createdDatabaseNames.add(databaseName);
        return databaseName;
    }

    /**
     * Builds the JDBC URL for a specific database inside the shared MySQL container.
     */
    private String buildJdbcUrl(String databaseName) {
        return BACKEND.jdbcUrlForDatabase(databaseName);
    }

    /**
     * Returns the JDBC URL of the MySQL admin database used to create and drop test databases.
     */
    private String adminJdbcUrl() {
        return BACKEND.adminJdbcUrl();
    }

    /**
     * Quotes a MySQL identifier safely.
     */
    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * Resolves the MySQL backend used by integration tests.
     */
    private record MySqlTestBackend(String adminJdbcUrl,
                                       String defaultJdbcUrl,
                                       String username,
                                       String password) {
        private static final String BACKEND_MODE_PROPERTY = "mesos.test.mysql.backend";
        private static final String BACKEND_MODE_ENV = "MESOS_TEST_MYSQL_BACKEND";
        private static final String DEFAULT_JDBC_URL =
                "jdbc:mysql://localhost:3306/mesos?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
        private static final String DEFAULT_ADMIN_JDBC_URL =
                "jdbc:mysql://localhost:3306/mysql?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
        private static final String DEFAULT_USERNAME = "root";
        private static final String DEFAULT_PASSWORD = "root";

        private static MySqlTestBackend detect() {
            BackendMode backendMode = BackendMode.resolve();
            MySqlTestBackend externalBackend = fromEnvironment();
            SQLException externalFailure = externalBackend.reachabilityFailure();
            RuntimeException containerFailure = null;

            if (backendMode != BackendMode.TESTCONTAINERS && externalFailure == null) {
                return externalBackend;
            }

            if (backendMode != BackendMode.EXTERNAL) {
                try {
                    MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
                            .withDatabaseName("mesos")
                            .withUsername("mesos")
                            .withPassword(DEFAULT_PASSWORD)
                            .withEnv("MYSQL_ROOT_PASSWORD", DEFAULT_PASSWORD)
                            .withCommand(
                                    "--default-time-zone=+00:00",
                                    "--character-set-server=utf8mb4",
                                    "--collation-server=utf8mb4_bin"
                            );
                    mysql.start();
                    return new MySqlTestBackend(
                            jdbcUrlForDatabase(mysql.getJdbcUrl(), "mysql"),
                            mysql.getJdbcUrl(),
                            DEFAULT_USERNAME,
                            DEFAULT_PASSWORD
                    );
                } catch (RuntimeException exception) {
                    containerFailure = exception;
                }
            }

            IllegalStateException failure = new IllegalStateException(
                    "MySQL integration tests require either a working Testcontainers Docker environment "
                            + "or a reachable MySQL instance configured via MESOS_DB_URL/MESOS_DB_ADMIN_URL "
                            + "(defaults: jdbc:mysql://localhost:3306/mesos and /mysql).",
                    containerFailure
            );
            failure.addSuppressed(externalFailure);
            throw failure;
        }

        private static MySqlTestBackend fromEnvironment() {
            return new MySqlTestBackend(
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

        private SQLException reachabilityFailure() {
            try (Connection ignored = DriverManager.getConnection(this.adminJdbcUrl, this.username, this.password)) {
                return null;
            } catch (SQLException exception) {
                return exception;
            }
        }

        private String jdbcUrlForDatabase(String databaseName) {
            return jdbcUrlForDatabase(this.defaultJdbcUrl, databaseName);
        }

        private static String jdbcUrlForDatabase(String templateJdbcUrl, String databaseName) {
            String prefix = "jdbc:mysql://";
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
                            "Unsupported MySQL test backend mode: " + rawValue
                    );
                };
            }
        }
    }
}
