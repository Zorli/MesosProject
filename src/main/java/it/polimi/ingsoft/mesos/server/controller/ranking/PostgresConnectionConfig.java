package it.polimi.ingsoft.mesos.server.controller.ranking;

/**
 * Immutable PostgreSQL connection settings used by the ranking persistence layer.
 *
 * @param jdbcUrl full JDBC URL of the PostgreSQL database
 * @param username database username
 * @param password database password
 */
public record PostgresConnectionConfig(String jdbcUrl, String username, String password) {

    /**
     * Validates connection settings.
     */
    public PostgresConnectionConfig {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC url cannot be null or blank");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Database username cannot be null or blank");
        }

        if (password == null) {
            throw new IllegalArgumentException("Database password cannot be null");
        }
    }
}
