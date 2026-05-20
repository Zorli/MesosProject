package it.polimi.ingsoft.mesos.server.factory;

import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultGameFactoryTest {

    @Test
    void createGameInitializesPlayerGameAttributes() {
        DefaultGameFactory gameFactory = DefaultGameFactory.getInstance();
        ArrayList<Player> players = new ArrayList<>();
        players.add(new Player("Alice", 0, 0, TotemColor.RED, null));
        players.add(new Player("Bob", 0, 0, TotemColor.BLUE, null));

        Game game = gameFactory.createGame(123, players);

        assertEquals(123, game.getId());
        assertEquals(64, game.getBoard().getRemainingTribeDeckCards().size());
        assertEquals(21, game.getBoard().getRemainingBuildingDeckCards().size());
        assertNotNull(players.get(0).getTribe());
        assertNotNull(players.get(1).getTribe());
    }
}
