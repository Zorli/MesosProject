package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRuntimeConfigTest {

    @Test
    void fromEnvironmentUsesDefaultsForLocalDevelopment() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of());

        assertEquals("jdbc:postgresql://localhost:5432/mesos", config.jdbcUrl());
        assertEquals("jdbc:postgresql://localhost:5432/postgres", config.adminJdbcUrl());
        assertEquals("postgres", config.username());
        assertEquals("postgres", config.password());
        assertEquals(3, config.maxBusyRetries());
        assertTrue(config.dbEnabled());
    }

    @Test
    void fromEnvironmentReadsExplicitOverrides() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of(
                "MESOS_DB_URL", "jdbc:postgresql://db.internal:5432/custom",
                "MESOS_DB_ADMIN_URL", "jdbc:postgresql://db.internal:5432/postgres",
                "MESOS_DB_USER", "mesos",
                "MESOS_DB_PASSWORD", "secret",
                "MESOS_DB_MAX_BUSY_RETRIES", "8"
        ));

        assertEquals("jdbc:postgresql://db.internal:5432/custom", config.jdbcUrl());
        assertEquals("jdbc:postgresql://db.internal:5432/postgres", config.adminJdbcUrl());
        assertEquals("mesos", config.username());
        assertEquals("secret", config.password());
        assertEquals(8, config.maxBusyRetries());
        assertTrue(config.dbEnabled());
    }

    @Test
    void fromEnvironmentRejectsInvalidRetryValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerRuntimeConfig.fromEnvironment(Map.of("MESOS_DB_MAX_BUSY_RETRIES", "abc"))
        );
    }

    @Test
    void fromEnvironmentDerivesAdminUrlAndDatabaseNameFromJdbcUrl() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of(
                "MESOS_DB_URL", "jdbc:postgresql://db.internal:5433/custom_db?sslmode=disable"
        ));

        assertEquals("jdbc:postgresql://db.internal:5433/postgres", config.adminJdbcUrl());
        assertEquals("custom_db", config.databaseName());
    }

    @Test
    void fromEnvironmentDisablesDatabaseWhenNoDbFlagIsTrue() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of("MESOS_NO_DB", "true"));

        assertFalse(config.dbEnabled());
    }

    @Test
    void canonicalConstructorRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig(null, "jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres", 3));
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig(" ", "jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres", 3));
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig("jdbc:postgresql://localhost:5432/mesos", null, "postgres", "postgres", 3));
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig("jdbc:postgresql://localhost:5432/mesos", " ", "postgres", "postgres", 3));
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig("jdbc:postgresql://localhost:5432/mesos", "jdbc:postgresql://localhost:5432/postgres", null, "postgres", 3));
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig("jdbc:postgresql://localhost:5432/mesos", "jdbc:postgresql://localhost:5432/postgres", " ", "postgres", 3));
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig("jdbc:postgresql://localhost:5432/mesos", "jdbc:postgresql://localhost:5432/postgres", "postgres", null, 3));
        assertThrows(IllegalArgumentException.class, () -> new ServerRuntimeConfig("jdbc:postgresql://localhost:5432/mesos", "jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres", -1));
    }

    @Test
    void toPostgresConnectionConfigPreservesJdbcUserAndPassword() {
        ServerRuntimeConfig config = new ServerRuntimeConfig(
                "jdbc:postgresql://db.internal:5432/mesos",
                "jdbc:postgresql://db.internal:5432/postgres",
                "mesos",
                "secret",
                4
        );

        PostgresConnectionConfig connectionConfig = config.toPostgresConnectionConfig();

        assertEquals("jdbc:postgresql://db.internal:5432/mesos", connectionConfig.jdbcUrl());
        assertEquals("mesos", connectionConfig.username());
        assertEquals("secret", connectionConfig.password());
    }

    @Test
    void databaseNameRejectsUnsupportedJdbcFormats() {
        ServerRuntimeConfig missingDatabase = new ServerRuntimeConfig(
                "jdbc:postgresql://db.internal:5432/",
                "jdbc:postgresql://db.internal:5432/postgres",
                "postgres",
                "postgres",
                3
        );
        ServerRuntimeConfig wrongPrefix = new ServerRuntimeConfig(
                "jdbc:mysql://db.internal:5432/mesos",
                "jdbc:postgresql://db.internal:5432/postgres",
                "postgres",
                "postgres",
                3
        );

        assertThrows(IllegalArgumentException.class, missingDatabase::databaseName);
        assertThrows(IllegalArgumentException.class, wrongPrefix::databaseName);
    }

    @Test
    void fromEnvironmentRejectsNullEnvironmentAndInvalidAdminDerivationSource() {
        assertThrows(IllegalArgumentException.class, () -> ServerRuntimeConfig.fromEnvironment(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerRuntimeConfig.fromEnvironment(Map.of("MESOS_DB_URL", "jdbc:postgresql://db.internal:5432/"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerRuntimeConfig.fromEnvironment(Map.of("MESOS_DB_URL", "jdbc:mysql://db.internal:5432/mesos"))
        );
    }
}
