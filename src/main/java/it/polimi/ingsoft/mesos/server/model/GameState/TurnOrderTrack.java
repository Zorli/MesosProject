package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

/*
This class is meant to be used as a tracker of the order to follow in the current round.
At the initialization of the game, after the player have chosen the starting block in the starting tile,
I can initialize the object.
Then I can use it to track who needs to play.
After the round I have to re-insert the new order provided from the offer path.
This operation is going to be completed in Game.
This class has to be instantied by the factory.
 */


public class TurnOrderTrack {
    private List<Player> turnOrder;
    private int currentPlayerIndex;
    private int roundNumber;
    private RoundPhase currentRoundPhase;

}
