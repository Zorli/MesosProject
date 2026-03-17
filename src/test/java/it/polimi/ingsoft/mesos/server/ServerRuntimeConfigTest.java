package it.polimi.ingsoft.mesos.server;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerRuntimeConfigTest {

    @Test
    void fromEnvironmentUsesDefaultsForLocalDevelopment() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of());

        assertEquals("jdbc:postgresql://localhost:5432/mesos", config.jdbcUrl());
        assertEquals("jdbc:postgresql://localhost:5432/postgres", config.adminJdbcUrl());
        assertEquals("postgres", config.username());
        assertEquals("postgres", config.password());
        assertEquals(3, config.maxBusyRetries());
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
}
