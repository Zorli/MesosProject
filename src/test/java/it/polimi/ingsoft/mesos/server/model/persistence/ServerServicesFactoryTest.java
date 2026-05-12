package it.polimi.ingsoft.mesos.server.model.persistence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;

class ServerServicesFactoryTest {

    @Test
    void noDbModeDisablesRankingServiceWithoutRemovingRankingCode() {
        ServerRuntimeConfig runtimeConfig = ServerRuntimeConfig.fromEnvironment(Map.of(
                "MESOS_NO_DB",
                "true"
        ));

        ServerServices services = ServerServicesFactory.create(runtimeConfig);

        assertSame(NoOpRankingService.INSTANCE, services.rankingService());
    }

    @Test
    void rejectsNullRuntimeConfig() {
        assertThrows(NullPointerException.class, () -> ServerServicesFactory.create(null));
    }

}
