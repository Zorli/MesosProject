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

/**
 * Tracks the order of players inside the current round and exposes the active player.
 *
 * <p>The tracker is initialized with the order produced by setup or by the end of a previous
 * round. It then advances one player at a time until the round is exhausted, while also keeping a
 * progressive round counter.
 */
public class TurnOrderTrack {
    private List<Player> turnOrder;
    private int currentPlayerIndex;
    private int roundNumber;

    /**
     * Creates an empty tracker that must later receive a non-empty turn order.
     */
    public TurnOrderTrack() {
        this.turnOrder = new ArrayList<Player>();
        this.currentPlayerIndex = 0;
        this.roundNumber = 0;
    }

    //To call after the round

    /**
     * Replaces the current round order and rewinds the active-player pointer to the first player.
     *
     * @param newOrder ordered list of players for the new round
     */
    public void setNewOrder(List<Player> newOrder) {
        if (newOrder == null || newOrder.isEmpty()) {
            throw new IllegalArgumentException("Turn order cannot be null or empty");
        }
        this.turnOrder = new ArrayList<Player>(newOrder); //I make a copy
        this.currentPlayerIndex = 0;
        this.roundNumber += 1;
    }

    /**
     * Returns the player whose turn is currently expected.
     *
     * @return active player in the current round
     */
    public Player getCurrentPlayer() {
        this.ensureOrderInitialized();
        return this.turnOrder.get(this.currentPlayerIndex);
    }

    /**
     * Returns the current round number.
     *
     * @return progressive round counter starting from one after the first order is set
     */
    public int getRoundNumber() {
        return this.roundNumber;
    }

    /**
     * Advances the tracker to the next player in the current round.
     */
    public void advanceToNextPlayer() {
        this.ensureOrderInitialized();
        if (this.isLastPlayer()) {
            throw new IllegalStateException("Cannot advance beyond the last player of the round");
        }
        this.currentPlayerIndex = this.currentPlayerIndex + 1;
    }

    /**
     * Returns whether the active player is the final player of the round order.
     *
     * @return {@code true} when no further players remain in the current round
     */
    public boolean isLastPlayer() {
        this.ensureOrderInitialized();
        return this.currentPlayerIndex == this.turnOrder.size() - 1;
    }

    /**
     * Ensures that a round order has been configured before accessing the tracker.
     */
    private void ensureOrderInitialized() {
        if (this.turnOrder.isEmpty()) {
            throw new IllegalStateException("Turn order has not been initialized");
        }
    }
}
