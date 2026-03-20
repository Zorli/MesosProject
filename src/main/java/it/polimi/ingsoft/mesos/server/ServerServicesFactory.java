package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingSchemaModule;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingService;
import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.PostgresSavedMatchSchemaModule;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.PostgresSavedMatchService;

import java.util.List;
import java.util.Objects;

/**
 * Factory responsible for wiring the runtime services selected for the current server mode.
 */
public final class ServerServicesFactory {
    /**
     * Prevents instantiation of the utility factory class.
     */
    private ServerServicesFactory() {
    }

    /**
     * Creates the runtime service container for the provided configuration.
     *
     * @param runtimeConfig runtime configuration
     * @return initialized runtime services
     */
    public static ServerServices create(ServerRuntimeConfig runtimeConfig) {
        Objects.requireNonNull(runtimeConfig, "Runtime config cannot be null");

        if (!runtimeConfig.dbEnabled()) {
            return new ServerServices(NoOpRankingService.INSTANCE, NoOpSavedMatchService.INSTANCE);
        }

        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(
                        PostgresRankingSchemaModule.getInstance(),
                        PostgresSavedMatchSchemaModule.getInstance()
                )
        );

        return new ServerServices(
                new PostgresRankingService(
                        runtimeConfig.toPostgresConnectionConfig(),
                        runtimeConfig.maxBusyRetries()
                ),
                new PostgresSavedMatchService(
                        runtimeConfig.toPostgresConnectionConfig(),
                        runtimeConfig.maxBusyRetries()
                )
        );
    }
}
