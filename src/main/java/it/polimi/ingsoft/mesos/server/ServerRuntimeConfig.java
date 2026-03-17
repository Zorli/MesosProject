package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;

import java.util.Map;

/**
 * Reads server runtime configuration from environment variables.
 */
public record ServerRuntimeConfig(String jdbcUrl,
                                  String adminJdbcUrl,
                                  String username,
                                  String password,
                                  int maxBusyRetries,
                                  boolean dbEnabled) {
    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:5432/mesos";
    private static final String DEFAULT_ADMIN_DATABASE = "postgres";
    private static final String DEFAULT_USERNAME = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";
    private static final int DEFAULT_MAX_BUSY_RETRIES = 3;
    private static final boolean DEFAULT_DB_ENABLED = true;

    /**
     * Backward-compatible constructor used by existing tests and bootstrap code.
     *
     * @param jdbcUrl JDBC URL for the target PostgreSQL database
     * @param adminJdbcUrl JDBC URL for the admin PostgreSQL database
     * @param username database username
     * @param password database password
     * @param maxBusyRetries maximum number of transient retry attempts
     */
    public ServerRuntimeConfig(String jdbcUrl,
                               String adminJdbcUrl,
                               String username,
                               String password,
                               int maxBusyRetries) {
        this(jdbcUrl, adminJdbcUrl, username, password, maxBusyRetries, DEFAULT_DB_ENABLED);
    }

    /**
     * Builds runtime configuration from environment variables with local-development defaults.
     *
     * @param environment source environment
     * @return validated runtime configuration
     */
    public static ServerRuntimeConfig fromEnvironment(Map<String, String> environment) {
        if (environment == null) {
            throw new IllegalArgumentException("Environment cannot be null");
        }

        String jdbcUrl = environment.getOrDefault("MESOS_DB_URL", DEFAULT_JDBC_URL);
        String adminJdbcUrl = environment.getOrDefault("MESOS_DB_ADMIN_URL", deriveAdminJdbcUrl(jdbcUrl));
        String username = environment.getOrDefault("MESOS_DB_USER", DEFAULT_USERNAME);
        String password = environment.getOrDefault("MESOS_DB_PASSWORD", DEFAULT_PASSWORD);
        String retriesValue = environment.getOrDefault(
                "MESOS_DB_MAX_BUSY_RETRIES",
                Integer.toString(DEFAULT_MAX_BUSY_RETRIES)
        );
        String dbEnabledValue = environment.getOrDefault("MESOS_NO_DB", Boolean.toString(!DEFAULT_DB_ENABLED));

        int maxBusyRetries;
        try {
            maxBusyRetries = Integer.parseInt(retriesValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MESOS_DB_MAX_BUSY_RETRIES must be an integer", exception);
        }

        boolean dbEnabled = !Boolean.parseBoolean(dbEnabledValue);
        return new ServerRuntimeConfig(jdbcUrl, adminJdbcUrl, username, password, maxBusyRetries, dbEnabled);
    }

    /**
     * Validates runtime configuration values.
     */
    public ServerRuntimeConfig {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC url cannot be null or blank");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Database username cannot be null or blank");
        }

        if (adminJdbcUrl == null || adminJdbcUrl.isBlank()) {
            throw new IllegalArgumentException("Admin JDBC url cannot be null or blank");
        }

        if (password == null) {
            throw new IllegalArgumentException("Database password cannot be null");
        }

        if (maxBusyRetries < 0) {
            throw new IllegalArgumentException("Max busy retries cannot be negative");
        }
    }

    /**
     * Converts this runtime config to the ranking module connection config.
     *
     * @return PostgreSQL connection configuration
     */
    public PostgresConnectionConfig toPostgresConnectionConfig() {
        return new PostgresConnectionConfig(this.jdbcUrl, this.username, this.password);
    }

    /**
     * Returns the target PostgreSQL database name extracted from the JDBC URL.
     *
     * @return configured target database name
     */
    public String databaseName() {
        return extractDatabaseName(this.jdbcUrl);
    }

    /**
     * Derives the admin JDBC URL by reusing host and port but switching to the default admin database.
     */
    private static String deriveAdminJdbcUrl(String jdbcUrl) {
        String prefix = "jdbc:postgresql://";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Cannot derive admin JDBC url from: " + jdbcUrl);
        }

        String remainder = jdbcUrl.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex < 0 || slashIndex == remainder.length() - 1) {
            throw new IllegalArgumentException("JDBC url must include a database name: " + jdbcUrl);
        }

        String hostAndPort = remainder.substring(0, slashIndex);
        return prefix + hostAndPort + "/" + DEFAULT_ADMIN_DATABASE;
    }

    /**
     * Extracts the database name from a PostgreSQL JDBC URL.
     */
    private static String extractDatabaseName(String jdbcUrl) {
        String prefix = "jdbc:postgresql://";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Unsupported JDBC url: " + jdbcUrl);
        }

        String remainder = jdbcUrl.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex < 0 || slashIndex == remainder.length() - 1) {
            throw new IllegalArgumentException("JDBC url must include a database name: " + jdbcUrl);
        }

        String databasePart = remainder.substring(slashIndex + 1);
        int queryIndex = databasePart.indexOf('?');
        String databaseName = queryIndex >= 0 ? databasePart.substring(0, queryIndex) : databasePart;

        if (databaseName.isBlank()) {
            throw new IllegalArgumentException("JDBC url must include a database name: " + jdbcUrl);
        }

        return databaseName;
    }
}
