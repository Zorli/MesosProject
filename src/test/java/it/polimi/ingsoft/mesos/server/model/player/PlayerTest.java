package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerTest {

    @Test
    void initializeGameAttributesCreatesTribeWhenMissing() {
        Player player = new Player("Alice", 0, 0, TotemColor.RED, null);

        player.initializeGameAttributes();

        assertNotNull(player.getTribe());
    }

    @Test
    void initializeGameAttributesKeepsExistingTribe() {
        Tribe tribe = new Tribe();
        Player player = new Player("Alice", 0, 0, TotemColor.RED, tribe);

        player.initializeGameAttributes();

        assertSame(tribe, player.getTribe());
    }
}
