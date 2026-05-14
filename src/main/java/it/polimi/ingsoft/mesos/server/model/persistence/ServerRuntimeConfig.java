package it.polimi.ingsoft.mesos.server.model.persistence;

import java.util.Map;
import java.util.Objects;

/** Server runtime configuration resolved from environment variables and Java system properties. */
public record ServerRuntimeConfig(
        boolean gamePersistenceEnabled,
        String gamePersistenceDirectory
) {
    private static final String DEFAULT_GAME_PERSISTENCE_DIRECTORY = "data/game-checkpoints";

    public ServerRuntimeConfig {
        Objects.requireNonNull(gamePersistenceDirectory, "gamePersistenceDirectory cannot be null");
        if (gamePersistenceDirectory.isBlank()) throw new IllegalArgumentException("gamePersistenceDirectory cannot be blank");
    }

    /**
     * Builds configuration from the process environment.
     * Java system properties ({@code -DKEY=value}) take precedence over environment variables.
     */
    public static ServerRuntimeConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment cannot be null");

        String persistence  = resolve(environment, "MESOS_GAME_PERSISTENCE_ENABLED", null);
        String persistDir   = resolve(environment, "MESOS_GAME_PERSISTENCE_DIR",     DEFAULT_GAME_PERSISTENCE_DIRECTORY);

        boolean gamePersistenceEnabled = persistence == null || persistence.isBlank() || Boolean.parseBoolean(persistence);

        return new ServerRuntimeConfig(
                gamePersistenceEnabled, persistDir
        );
    }

    /** Resolves a key: Java system property wins, then env var, then {@code defaultValue}. */
    private static String resolve(Map<String, String> env, String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;
        String envVal = env.get(key);
        return (envVal != null && !envVal.isBlank()) ? envVal : defaultValue;
    }
}
