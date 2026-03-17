package it.polimi.ingsoft.mesos.server.model.Orchestration;


import it.polimi.ingsoft.mesos.server.model.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;


//Need to instantiate a game using the factory and to move the players from the lobby to the game

/**
 * Coordinates one running match from the perspective of external callers.
 *
 * <p>The orchestrator is responsible for owning the {@link Game} created for a lobby and keeping
 * the list of players participating in that match. Higher layers are expected to route all
 * game-specific requests through this class so that access checks and orchestration logic stay
 * centralized.
 */
public class GameOrchestrator {
    Game game;
    List<Player> players;
    //   For whoever is going to code this:
    //1) Use the factory passing the number of players as shown
    //2) Make sure the game is correctly initialized
    //3) Call setup() on game so every player can choose its own starting point
    //4)This class should be the main entry point for the client playing in that specific game, so basically here
    // we need to perform basics checks on the correctnes of the request and moreover if the player is
    // for real in the match.
    //5)Call the requested method from the client in the game class

    /**
     * Creates an orchestrator for a new match using the provided game factory and player list.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     */
    public GameOrchestrator(GameFactory gameFactory, ArrayList<Player> players) {
        this.game = gameFactory.createGame(players);
        this.players = players;
    }



    //RMI Protocols istances here. All the communication between the client in a specific match and the match should
    //Pass through this controller. The gameOrchestrator(aka the game controller for a specific match)
    //is the entry point that delegates validated requests to the underlying Game.



}
