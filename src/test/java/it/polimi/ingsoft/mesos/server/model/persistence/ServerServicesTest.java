package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.NoOpSavedMatchService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerServicesTest {

    @Test
    void recordRejectsNullDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new ServerServices(null, NoOpSavedMatchService.INSTANCE));
        assertThrows(IllegalArgumentException.class, () -> new ServerServices(NoOpRankingService.INSTANCE, null));
    }

    @Test
    void recordExposesInjectedDependencies() {
        ServerServices services = new ServerServices(NoOpRankingService.INSTANCE, NoOpSavedMatchService.INSTANCE);

        assertSame(NoOpRankingService.INSTANCE, services.rankingService());
        assertSame(NoOpSavedMatchService.INSTANCE, services.savedMatchService());
    }
}
