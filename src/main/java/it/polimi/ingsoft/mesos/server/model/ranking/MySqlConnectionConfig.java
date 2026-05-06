package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Immutable MySQL connection settings used by the ranking persistence layer.
 *
 * @param jdbcUrl full JDBC URL of the MySQL database
 * @param username database username
 * @param password database password
 */
public record MySqlConnectionConfig(String jdbcUrl, String username, String password) {

    /**
     * Validates connection settings.
     */
    public MySqlConnectionConfig {
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
