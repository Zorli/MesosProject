package it.polimi.ingsoft.mesos.server.model.persistence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerServicesFactoryTest {

    @Test
    void noDbModeDisablesRankingServiceWithoutRemovingRankingCode() {
        ServerRuntimeConfig runtimeConfig = ServerRuntimeConfig.fromEnvironment(Map.of(
                "MESOS_RANKING_DB_ENABLED",
                "false"
        ));

        ServerServices services = ServerServicesFactory.create(runtimeConfig);

        assertNull(services.rankingService());
    }

    @Test
    void rejectsNullRuntimeConfig() {
        assertThrows(NullPointerException.class, () -> ServerServicesFactory.create(null));
    }

}
