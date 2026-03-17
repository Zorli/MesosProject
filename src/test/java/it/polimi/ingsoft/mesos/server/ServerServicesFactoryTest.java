package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.NoOpSavedMatchService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ServerServicesFactoryTest {

    @Test
    void createReturnsNoOpServicesWhenDatabaseIsDisabled() {
        ServerRuntimeConfig runtimeConfig = new ServerRuntimeConfig(
                "jdbc:postgresql://localhost:5432/mesos",
                "jdbc:postgresql://localhost:5432/postgres",
                "postgres",
                "postgres",
                3,
                false
        );

        ServerServices services = ServerServicesFactory.create(runtimeConfig);

        assertSame(NoOpRankingService.INSTANCE, services.rankingService());
        assertSame(NoOpSavedMatchService.INSTANCE, services.savedMatchService());
    }
}
