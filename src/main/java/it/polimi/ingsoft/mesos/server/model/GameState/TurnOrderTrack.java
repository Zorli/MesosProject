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

    /**
     * Creates a turn-order tracker from an already persisted runtime snapshot.
     *
     * @param turnOrder ordered players of the current round
     * @param currentPlayerIndex index of the next player to act
     * @param roundNumber current round number
     * @param currentRoundPhase current round phase
     */
    public TurnOrderTrack(List<Player> turnOrder,
                          int currentPlayerIndex,
                          int roundNumber,
                          RoundPhase currentRoundPhase) {
        if (turnOrder == null || turnOrder.isEmpty()) {
            throw new IllegalArgumentException("Turn order cannot be null or empty");
        }
        if (currentPlayerIndex < 0 || currentPlayerIndex >= turnOrder.size()) {
            throw new IllegalArgumentException("Current player index is out of bounds");
        }

        this.turnOrder = new ArrayList<>(turnOrder);
        this.currentPlayerIndex = currentPlayerIndex;
        this.roundNumber = roundNumber;
        this.currentRoundPhase = currentRoundPhase;
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

    public List<Player> getTurnOrder() {
        this.ensureOrderInitialized();
        return new ArrayList<>(this.turnOrder);
    }

    public int getCurrentPlayerIndex() {
        this.ensureOrderInitialized();
        return this.currentPlayerIndex;
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
