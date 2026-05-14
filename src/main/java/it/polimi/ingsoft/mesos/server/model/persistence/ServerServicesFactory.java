package it.polimi.ingsoft.mesos.server.model.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.persistence.game.FileMatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;

import java.nio.file.Path;
import java.util.Objects;

/** Wires the runtime services selected by the current server configuration. */
public final class ServerServicesFactory {
    private ServerServicesFactory() {}

    public static ServerServices create(ServerRuntimeConfig config) {
        Objects.requireNonNull(config, "config cannot be null");

        MatchPersistence matchPersistence = new FileMatchPersistence(
                config.gamePersistenceEnabled(),
                Path.of(config.gamePersistenceDirectory()),
                new ObjectMapper()
        );

        return new ServerServices(matchPersistence);
    }
}
