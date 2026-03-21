package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingService;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.PostgresSavedMatchService;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Tag("integration")
class ServerServicesFactoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void createBuildsPostgresBackedServicesWhenDatabaseIsEnabled() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("server_services_factory", false);

        ServerServices services = ServerServicesFactory.create(runtimeConfig);

        assertInstanceOf(PostgresRankingService.class, services.rankingService());
        assertInstanceOf(PostgresSavedMatchService.class, services.savedMatchService());
    }
}
