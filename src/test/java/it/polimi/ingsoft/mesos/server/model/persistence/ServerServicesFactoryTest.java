package it.polimi.ingsoft.mesos.server.model.persistence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerServicesFactoryTest {

    @Test
    void createsMatchPersistenceService() {
        ServerRuntimeConfig runtimeConfig = ServerRuntimeConfig.fromEnvironment(Map.of());

        ServerServices services = ServerServicesFactory.create(runtimeConfig);

        assertNotNull(services.matchPersistence());
    }

    @Test
    void rejectsNullRuntimeConfig() {
        assertThrows(NullPointerException.class, () -> ServerServicesFactory.create(null));
    }

}
