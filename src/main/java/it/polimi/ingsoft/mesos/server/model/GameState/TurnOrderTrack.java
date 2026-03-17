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

    // Constructors

    public TurnOrderTrack() {
        this.turnOrder = new ArrayList<>();
        this.currentPlayerIndex = 0;
        this.roundNumber = 0;
        this.currentRoundPhase = RoundPhase.PLACING_TOTEMS;
    }

    // Getters
    
    public Player getCurrentPlayer() {
        this.ensureOrderInitialized();
        return this.turnOrder.get(this.currentPlayerIndex);
    }

    public int getRoundNumber() {
        return this.roundNumber;
    }

    public RoundPhase getCurrentRoundPhase() {
        return this.currentRoundPhase;
    }

    // Setters

    public void setNewOrder(List<Player> newOrder) {
        if (newOrder == null || newOrder.isEmpty()) {
            throw new IllegalArgumentException("Turn order cannot be null or empty");
        }
        this.turnOrder = new ArrayList<>(newOrder);
        this.currentPlayerIndex = 0;
    }

    public void setCurrentRoundPhase(RoundPhase currentRoundPhase) {
        this.currentRoundPhase = currentRoundPhase;
    }

    // Public methods

    public void advanceToNextPlayer() {
        this.ensureOrderInitialized();
        if (this.isLastPlayer()) {
            throw new IllegalStateException("Cannot advance beyond the last player of the round");
        }
        this.currentPlayerIndex = this.currentPlayerIndex + 1;
    }

    public boolean isLastPlayer() {
        this.ensureOrderInitialized();
        return this.currentPlayerIndex == this.turnOrder.size() - 1;
    }

    public void advanceRoundNumber() {
        this.roundNumber = this.roundNumber + 1;
    }

    // Private methods

    private void ensureOrderInitialized() {
        if (this.turnOrder.isEmpty()) {
            throw new IllegalStateException("Turn order has not been initialized");
        }
    }

}
