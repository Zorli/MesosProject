package it.polimi.ingsoft.mesos.server.model.persistence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRuntimeConfigTest {

    @Test
    void defaultsEnableGamePersistence() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of());

        assertTrue(config.gamePersistenceEnabled());
        assertEquals("data/game-checkpoints", config.gamePersistenceDirectory());
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
