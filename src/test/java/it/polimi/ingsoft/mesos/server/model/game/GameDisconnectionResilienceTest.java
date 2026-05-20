package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression tests for game setup when players disconnect before the match
 * starts. They verify the public turn order and connection map, not the
 * internal recovery algorithm.
 */
class GameDisconnectionResilienceTest {

    /**
     * A player disconnected before setup is excluded from the first active
     * phase order, so the game starts from the first connected player.
     */
    @Test
    void playerDisconnectedBeforeSetupIsNotCurrentPlayerAfterSetup() {
        Player alice = new Player("Alice", 0, 0, TotemColor.RED);
        Player bob = new Player("Bob", 0, 0, TotemColor.BLUE);
        Player carol = new Player("Carol", 0, 0, TotemColor.WHITE);
        Game game = DefaultGameFactory.getInstance().createGame(1, List.of(alice, bob, carol));

        game.disconnectedPlayer(alice);
        game.setup();

        assertFalse(game.getConnectedPlayers().get(alice));
        assertEquals("Bob", game.getRoundFlow().getCurrentPlayer().getNickname());
        assertEquals(List.of(bob, carol), game.getRoundFlow().getCurrentPhaseOrder());
    }

    /**
     * If the player reconnects before setup, the original setup order is kept.
     */
    @Test
    void playerReconnectedBeforeSetupKeepsNormalInitialOrder() {
        Player alice = new Player("Alice", 0, 0, TotemColor.RED);
        Player bob = new Player("Bob", 0, 0, TotemColor.BLUE);
        Game game = DefaultGameFactory.getInstance().createGame(1, List.of(alice, bob));

        game.disconnectedPlayer(alice);
        game.reconnectedPlayer(alice);
        game.setup();

        assertEquals(List.of(alice, bob), game.getRoundFlow().getCurrentPhaseOrder());
    }
}
