package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.controller.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the order of play and round phase of the current match round.
 *
 * <p>The tracker is initialized by the game factory and then updated by {@link Game} as players
 * place totems, draw cards, and transition to later phases. At the end of each round the order
 * can be replaced with the resolution order returned by the offer path.
 */
public class TurnOrderTrack {
    private List<Player> turnOrder;
    private int currentPlayerIndex;
    private int roundNumber;
    private RoundPhase currentRoundPhase;

    // Constructors

    /**
     * Creates an empty tracker waiting for the first round order to be assigned.
     */
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

    /**
     * Returns the player whose action is currently expected.
     *
     * @return current player in turn order
     */
    public Player getCurrentPlayer() {
        this.ensureOrderInitialized();
        return this.turnOrder.get(this.currentPlayerIndex);
    }

    /**
     * Returns the number of the round currently being played.
     *
     * @return current round number
     */
    public int getRoundNumber() {
        return this.roundNumber;
    }

    /**
     * Returns the active phase inside the current round.
     *
     * @return current round phase
     */
    public RoundPhase getCurrentRoundPhase() {
        return this.currentRoundPhase;
    }

    /**
     * Returns a defensive copy of the current round order.
     *
     * @return ordered players for the active round
     */
    public List<Player> getTurnOrder() {
        this.ensureOrderInitialized();
        return new ArrayList<>(this.turnOrder);
    }

    /**
     * Returns the index of the player who must currently act.
     *
     * @return zero-based current-player index
     */
    public int getCurrentPlayerIndex() {
        this.ensureOrderInitialized();
        return this.currentPlayerIndex;
    }

    // Setters

    /**
     * Replaces the round order and resets the current-player cursor to the first player.
     *
     * @param newOrder order to use for the current round
     */
    public void setNewOrder(List<Player> newOrder) {
        if (newOrder == null || newOrder.isEmpty()) {
            throw new IllegalArgumentException("Turn order cannot be null or empty");
        }
        this.turnOrder = new ArrayList<>(newOrder);
        this.currentPlayerIndex = 0;
    }

    /**
     * Updates the active phase of the round.
     *
     * @param currentRoundPhase phase reached by the round flow
     */
    public void setCurrentRoundPhase(RoundPhase currentRoundPhase) {
        this.currentRoundPhase = currentRoundPhase;
    }

    // Public methods

    /**
     * Moves the cursor to the next player in the current order.
     *
     * @throws IllegalStateException if the tracker is already on the last player
     */
    public void advanceToNextPlayer() {
        this.ensureOrderInitialized();
        if (this.isLastPlayer()) {
            throw new IllegalStateException("Cannot advance beyond the last player of the round");
        }
        this.currentPlayerIndex = this.currentPlayerIndex + 1;
    }

    /**
     * Returns whether the cursor already points to the last player of the round.
     *
     * @return {@code true} when the current player is the final one in the order
     */
    public boolean isLastPlayer() {
        this.ensureOrderInitialized();
        return this.currentPlayerIndex == this.turnOrder.size() - 1;
    }

    /**
     * Increments the round number after a new round has been prepared.
     */
    public void advanceRoundNumber() {
        this.roundNumber = this.roundNumber + 1;
    }

    // Private methods

    /**
     * Ensures that a non-empty order has already been assigned to the tracker.
     */
    private void ensureOrderInitialized() {
        if (this.turnOrder.isEmpty()) {
            throw new IllegalStateException("Turn order has not been initialized");
        }
    }

}
