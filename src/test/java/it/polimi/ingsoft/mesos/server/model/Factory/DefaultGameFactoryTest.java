package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultGameFactoryTest {

    @Test
    void createGameInitializesPlayerGameAttributes() {
        DefaultGameFactory gameFactory = DefaultGameFactory.getInstance();
        ArrayList<Player> players = new ArrayList<>();
        players.add(new Player("Alice", 0, 0, TotemColor.RED, null));
        players.add(new Player("Bob", 0, 0, TotemColor.BLUE, null));

        gameFactory.createGame(players);

        assertNotNull(players.get(0).getTribe());
        assertNotNull(players.get(1).getTribe());
    }
}
