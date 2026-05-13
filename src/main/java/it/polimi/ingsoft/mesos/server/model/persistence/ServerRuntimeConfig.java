package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;

import java.util.Map;
import java.util.Objects;

/** Server runtime configuration resolved from environment variables and Java system properties. */
public record ServerRuntimeConfig(
        String jdbcUrl,
        String adminJdbcUrl,
        String username,
        String password,
        int maxBusyRetries,
        boolean dbEnabled,
        boolean gamePersistenceEnabled,
        String gamePersistenceDirectory
) {
    private static final String JDBC_PREFIX = "jdbc:postgresql://";
    private static final String DEFAULT_JDBC_URL = JDBC_PREFIX + "localhost:5432/mesos";
    private static final String DEFAULT_ADMIN_DATABASE = "postgres";
    private static final String DEFAULT_USERNAME = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";
    private static final int DEFAULT_MAX_BUSY_RETRIES = 3;
    private static final String DEFAULT_GAME_PERSISTENCE_DIRECTORY = "data/game-checkpoints";

    public ServerRuntimeConfig {
        Objects.requireNonNull(jdbcUrl,                "jdbcUrl cannot be null");
        Objects.requireNonNull(adminJdbcUrl,           "adminJdbcUrl cannot be null");
        Objects.requireNonNull(username,               "username cannot be null");
        Objects.requireNonNull(password,               "password cannot be null");
        Objects.requireNonNull(gamePersistenceDirectory, "gamePersistenceDirectory cannot be null");
        if (jdbcUrl.isBlank())                throw new IllegalArgumentException("jdbcUrl cannot be blank");
        if (adminJdbcUrl.isBlank())           throw new IllegalArgumentException("adminJdbcUrl cannot be blank");
        if (username.isBlank())               throw new IllegalArgumentException("username cannot be blank");
        if (gamePersistenceDirectory.isBlank()) throw new IllegalArgumentException("gamePersistenceDirectory cannot be blank");
        if (maxBusyRetries < 0)               throw new IllegalArgumentException("maxBusyRetries cannot be negative");
    }

    /**
     * Builds configuration from the process environment.
     * Java system properties ({@code -DKEY=value}) take precedence over environment variables.
     */
    public static ServerRuntimeConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment cannot be null");

        String jdbcUrl      = resolve(environment, "MESOS_DB_URL",                   DEFAULT_JDBC_URL);
        String adminJdbcUrl = resolve(environment, "MESOS_DB_ADMIN_URL",             deriveAdminJdbcUrl(jdbcUrl));
        String username     = resolve(environment, "MESOS_DB_USER",                  DEFAULT_USERNAME);
        String password     = resolve(environment, "MESOS_DB_PASSWORD",              DEFAULT_PASSWORD);
        String retries      = resolve(environment, "MESOS_DB_MAX_BUSY_RETRIES",      String.valueOf(DEFAULT_MAX_BUSY_RETRIES));
        String noDb         = resolve(environment, "MESOS_NO_DB",                    "false");
        String persistence  = resolve(environment, "MESOS_GAME_PERSISTENCE_ENABLED", null);
        String persistDir   = resolve(environment, "MESOS_GAME_PERSISTENCE_DIR",     DEFAULT_GAME_PERSISTENCE_DIRECTORY);

        int maxBusyRetries;
        try {
            maxBusyRetries = Integer.parseInt(retries);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("MESOS_DB_MAX_BUSY_RETRIES must be an integer", e);
        }

        boolean dbEnabled = !Boolean.parseBoolean(noDb);
        boolean gamePersistenceEnabled = persistence == null || persistence.isBlank() || Boolean.parseBoolean(persistence);

        return new ServerRuntimeConfig(
                jdbcUrl, adminJdbcUrl, username, password,
                maxBusyRetries, dbEnabled, gamePersistenceEnabled, persistDir
        );
    }

    /** Returns the PostgreSQL connection config for the ranking module. */
    public PostgresConnectionConfig toPostgresConnectionConfig() {
        return new PostgresConnectionConfig(this.jdbcUrl, this.username, this.password);
    }

    /** Returns the database name extracted from the JDBC URL. */
    public String databaseName() {
        return jdbcDatabaseName(this.jdbcUrl);
    }

    // -------------------------------------------------------------------------

    /** Resolves a key: Java system property wins, then env var, then {@code defaultValue}. */
    private static String resolve(Map<String, String> env, String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;
        String envVal = env.get(key);
        return (envVal != null && !envVal.isBlank()) ? envVal : defaultValue;
    }

    private static String deriveAdminJdbcUrl(String jdbcUrl) {
        return JDBC_PREFIX + jdbcHostAndPort(jdbcUrl) + "/" + DEFAULT_ADMIN_DATABASE;
    }

    private static String jdbcHostAndPort(String jdbcUrl) {
        String path = requirePostgresJdbcUrl(jdbcUrl);
        int slash = path.indexOf('/');
        if (slash <= 0) throw new IllegalArgumentException("JDBC url is missing host:port segment: " + jdbcUrl);
        return path.substring(0, slash);
    }

    private static String jdbcDatabaseName(String jdbcUrl) {
        String path = requirePostgresJdbcUrl(jdbcUrl);
        int slash = path.indexOf('/');
        if (slash < 0 || slash == path.length() - 1)
            throw new IllegalArgumentException("JDBC url is missing database name: " + jdbcUrl);
        String dbPart = path.substring(slash + 1);
        int query = dbPart.indexOf('?');
        String name = query >= 0 ? dbPart.substring(0, query) : dbPart;
        if (name.isBlank()) throw new IllegalArgumentException("JDBC url is missing database name: " + jdbcUrl);
        return name;
    }

    private static String requirePostgresJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(JDBC_PREFIX))
            throw new IllegalArgumentException("Unsupported JDBC url: " + jdbcUrl);
        return jdbcUrl.substring(JDBC_PREFIX.length());
    }
}
