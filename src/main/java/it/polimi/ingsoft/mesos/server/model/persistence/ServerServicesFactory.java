package it.polimi.ingsoft.mesos.server.model.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.model.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingSchemaModule;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingService;
import it.polimi.ingsoft.mesos.server.persistence.game.FileMatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;

import java.nio.file.Path;
import java.util.List;
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

        if (!config.dbEnabled()) {
            return new ServerServices(NoOpRankingService.INSTANCE, matchPersistence);
        }

        PostgresDatabaseBootstrap.getInstance().ensureReady(
                config,
                List.of(PostgresRankingSchemaModule.getInstance())
        );

        return new ServerServices(
                new PostgresRankingService(config.toPostgresConnectionConfig(), config.maxBusyRetries()),
                matchPersistence
        );
    }
}
