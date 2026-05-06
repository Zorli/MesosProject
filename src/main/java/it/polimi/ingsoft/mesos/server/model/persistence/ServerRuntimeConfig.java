package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.model.ranking.MySqlConnectionConfig;

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
    private static final String DEFAULT_JDBC_URL =
            "jdbc:mysql://localhost:3306/mesos?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DEFAULT_ADMIN_DATABASE = "mysql";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "root";
    private static final int DEFAULT_MAX_BUSY_RETRIES = 3;
    private static final boolean DEFAULT_DB_ENABLED = true;
    private static final String ENV_JDBC_URL = "MESOS_RANKING_DB_URL";
    private static final String ENV_ADMIN_JDBC_URL = "MESOS_RANKING_DB_ADMIN_URL";
    private static final String ENV_USERNAME = "MESOS_RANKING_DB_USER";
    private static final String ENV_PASSWORD = "MESOS_RANKING_DB_PASSWORD";
    private static final String ENV_MAX_BUSY_RETRIES = "MESOS_RANKING_DB_MAX_BUSY_RETRIES";
    private static final String ENV_ENABLED = "MESOS_RANKING_DB_ENABLED";

    /**
     * Backward-compatible constructor used by existing tests and bootstrap code.
     *
     * @param jdbcUrl JDBC URL for the target MySQL database
     * @param adminJdbcUrl JDBC URL for the admin MySQL database
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

        String jdbcUrl = valueOrDefault(environment, ENV_JDBC_URL, DEFAULT_JDBC_URL);
        String adminJdbcUrl = valueOrDefault(environment, ENV_ADMIN_JDBC_URL, deriveAdminJdbcUrl(jdbcUrl));
        String username = valueOrDefault(environment, ENV_USERNAME, DEFAULT_USERNAME);
        String password = valueOrDefault(environment, ENV_PASSWORD, DEFAULT_PASSWORD);

        int maxBusyRetries = parseMaxBusyRetries(environment);
        boolean dbEnabled = parseBoolean(environment.get(ENV_ENABLED), DEFAULT_DB_ENABLED);

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
     * @return MySQL connection configuration
     */
    public MySqlConnectionConfig toMySqlConnectionConfig() {
        return new MySqlConnectionConfig(this.jdbcUrl, this.username, this.password);
    }

    /**
     * Returns the target MySQL database name extracted from the JDBC URL.
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
        String prefix = "jdbc:mysql://";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Cannot derive admin JDBC url from: " + jdbcUrl);
        }

        String remainder = jdbcUrl.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex < 0 || slashIndex == remainder.length() - 1) {
            throw new IllegalArgumentException("JDBC url must include a database name: " + jdbcUrl);
        }

        String hostAndPort = remainder.substring(0, slashIndex);
        String databaseAndQuery = remainder.substring(slashIndex + 1);
        int queryIndex = databaseAndQuery.indexOf('?');
        String querySuffix = queryIndex >= 0 ? databaseAndQuery.substring(queryIndex) : "";
        return prefix + hostAndPort + "/" + DEFAULT_ADMIN_DATABASE + querySuffix;
    }

    /**
     * Extracts the database name from a MySQL JDBC URL.
     */
    private static String extractDatabaseName(String jdbcUrl) {
        String prefix = "jdbc:mysql://";
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

    /**
     * Reads a ranking database environment variable.
     */
    private static String valueOrDefault(Map<String, String> environment, String key, String defaultValue) {
        String value = environment.get(key);
        if (value != null && !value.isBlank()) {
            return value;
        }

        return defaultValue;
    }

    /**
     * Resolves whether ranking persistence should be enabled.
     */
    private static int parseMaxBusyRetries(Map<String, String> environment) {
        String retriesValue = valueOrDefault(
                environment,
                ENV_MAX_BUSY_RETRIES,
                Integer.toString(DEFAULT_MAX_BUSY_RETRIES)
        );

        try {
            return Integer.parseInt(retriesValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(ENV_MAX_BUSY_RETRIES + " must be an integer", exception);
        }
    }

    private static boolean parseBoolean(String rawValue, boolean defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : Boolean.parseBoolean(rawValue);
    }
}
