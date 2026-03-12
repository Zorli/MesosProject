package it.polimi.ingsoft.mesos.server.model.Orchestration;


import it.polimi.ingsoft.mesos.server.model.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;


//Need to instantiate a game using the factory and to move the players from the lobby to the game
public class GameOrchestrator {
    Game game;
    //   For whoever is going to code this:
    //1) Use the factory passing the number of players as shown
    //2) Make sure the game is correctly initialized
    //3) Call setup() on game so every player can choose its own starting point
    //4)This class should be the main entry point for the client playing in that specific game, so basically here
    // we need to perform basics checks on the correctnes of the request and moreover if the player is
    // for real in the match.
    //5)Call the requested method from the client in the game class
    public GameOrchestrator(GameFactory gameFactory, ArrayList<Player> players) {
        this.game = gameFactory.createGame(players);
    }

}
