package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.controller.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServices;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServicesFactory;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.NoOpSavedMatchService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void createRejectsNullRuntimeConfig() {
        assertThrows(NullPointerException.class, () -> ServerServicesFactory.create(null));
    }
}
