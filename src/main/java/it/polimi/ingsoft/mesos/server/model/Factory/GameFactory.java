package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

/**
 * Contract for components that bootstrap a full server-side game instance from
 * the list of players that joined a match.
 */
public interface GameFactory {
    /**
     * Creates a new game for the provided players.
     *
     * @param players players that will participate in the match
     * @return a newly initialized game instance
     */
    Game createGame(ArrayList<Player> players);
}
