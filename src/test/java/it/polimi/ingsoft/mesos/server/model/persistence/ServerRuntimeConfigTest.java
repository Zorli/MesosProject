package it.polimi.ingsoft.mesos.server.model.persistence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRuntimeConfigTest {

    @Test
    void defaultsPointToLocalPostgres() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of());

        assertEquals("jdbc:postgresql://localhost:5432/mesos", config.jdbcUrl());
        assertEquals("jdbc:postgresql://localhost:5432/postgres", config.adminJdbcUrl());
        assertEquals("postgres", config.username());
        assertEquals("postgres", config.password());
        assertEquals("mesos", config.databaseName());
        assertTrue(config.dbEnabled());
        assertTrue(config.gamePersistenceEnabled());
        assertEquals("data/game-checkpoints", config.gamePersistenceDirectory());
    }

    @Test
    void disablesDatabaseWithNoDbFlag() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of("MESOS_NO_DB", "true"));

        assertFalse(config.dbEnabled());
    }

    @Test
    void gamePersistenceCanBeDisabledViaEnv() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(
                Map.of("MESOS_GAME_PERSISTENCE_ENABLED", "false")
        );

        assertFalse(config.gamePersistenceEnabled());
    }

    @Test
    void gamePersistenceDirectoryCanBeOverriddenViaEnv() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(
                Map.of("MESOS_GAME_PERSISTENCE_DIR", "/tmp/checkpoints")
        );

        assertEquals("/tmp/checkpoints", config.gamePersistenceDirectory());
    }
}
