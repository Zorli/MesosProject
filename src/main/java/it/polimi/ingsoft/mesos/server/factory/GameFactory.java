package it.polimi.ingsoft.mesos.server.factory;

import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.List;

/**
 * Contract for components that bootstrap a full preLobby-side game instance from
 * the list of players that joined a match.
 */
public interface GameFactory {
    /**
     * Creates a new game for the provided players using game ID 0.
     * Intended for test use only — production code must always supply an explicit game ID.
     *
     * @param players players that will participate in the match
     * @return a newly initialized game instance
     */
    default Game createGame(List<Player> players) {
        return this.createGame(0, players);
    }

    /**
     * Creates a new game with the provided identifier.
     *
     * <p>Lobby-driven matches use the lobby id as the game id so routing and logs stay aligned
     * across the lobby-to-game transition.
     *
     * @param gameId identifier to assign to the new game
     * @param players players that will participate in the match
     * @return a newly initialized game instance
     */
    Game createGame(int gameId, List<Player> players);
}
