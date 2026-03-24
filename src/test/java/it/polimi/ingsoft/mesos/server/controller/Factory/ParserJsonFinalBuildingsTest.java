package it.polimi.ingsoft.mesos.server.controller.Factory;

import it.polimi.ingsoft.mesos.server.controller.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.BigPpReward;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.MultiplierForType;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.PrestigePointsForSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ParserJsonFinalBuildingsTest {

    @Test
    void card109IsPrestigePointsForSet() {
        assertInstanceOf(PrestigePointsForSet.class, ParserJson.getDeckTemplate().createCardById(109));
    }

    @Test
    void card110IsMultiplierForType() {
        assertInstanceOf(MultiplierForType.class, ParserJson.getDeckTemplate().createCardById(110));
    }

    @Test
    void card117IsBigPpReward() {
        assertInstanceOf(BigPpReward.class, ParserJson.getDeckTemplate().createCardById(117));
    }
}
