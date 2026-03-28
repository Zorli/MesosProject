package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerTest {

    @Test
    void constructorCreatesFreshTribeWhenMissing() {
        Player player = new Player("Alice", 0, 0, TotemColor.RED);

        assertNotNull(player.getTribe());
    }

    @Test
    void overloadedConstructorKeepsProvidedTribeInstance() {
        Tribe tribe = new Tribe();

        Player player = new Player("Alice", 0, 0, TotemColor.RED, tribe);

        assertSame(tribe, player.getTribe());
    }
}
