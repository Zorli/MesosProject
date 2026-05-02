package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.model.persistence.mysql.MySqlDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.model.ranking.MySqlRankingSchemaModule;
import it.polimi.ingsoft.mesos.server.model.ranking.MySqlRankingService;

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
            return new ServerServices(null);
        }

        MySqlDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(MySqlRankingSchemaModule.getInstance())
        );

        return new ServerServices(
                new MySqlRankingService(
                        runtimeConfig.toMySqlConnectionConfig(),
                        runtimeConfig.maxBusyRetries()
                )
        );
    }
}
