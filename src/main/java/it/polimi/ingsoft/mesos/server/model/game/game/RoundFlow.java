package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the round-level flow of a game.
 *
 * <p>This class knows which phase of the round is active, which players are
 * allowed to act in that phase, which player is currently expected to act, and
 * which round number is being played.
 *
 * <p>The player order is the order of the current phase, not a single fixed
 * order for the whole round. During one round the game can use different
 * orders:
 * <ul>
 *     <li>during {@link RoundPhase#PLACING_TOTEMS}, players act in the starting
 *     tile order;</li>
 *     <li>during {@link RoundPhase#PLAYER_DRAWS}, players act in the offer-path
 *     resolution order;</li>
 *     <li>during {@link RoundPhase#EXTRA_DRAWS}, only players eligible for an
 *     extra draw act, usually following next-round order.</li>
 * </ul>
 *
 * <p>It does not know the rules of totem placement, card drafting, extra draws,
 * effects, or scoring. Those rules belong to higher-level game flow classes.
 */
public class RoundFlow {
    private List<Player> currentPhaseOrder;
    private int currentPlayerIndex;
    private int roundNumber;
    private RoundPhase currentRoundPhase;

    /**
     * Creates an empty round flow.
     *
     * <p>The order is intentionally empty because the first real order is known
     * only after game setup.
     */
    public RoundFlow() {
        this.currentPhaseOrder = new ArrayList<>();
        this.currentPlayerIndex = 0;
        this.roundNumber = 0;
        this.currentRoundPhase = RoundPhase.PLACING_TOTEMS;
    }

    /**
     * Returns the player who must currently act.
     *
     * @return current player
     */
    public Player getCurrentPlayer() {
        this.ensureCurrentPhaseOrderInitialized();
        return this.currentPhaseOrder.get(this.currentPlayerIndex);
    }

    /**
     * Returns the current round number.
     *
     * @return current round number
     */
    public int getRoundNumber() {
        return this.roundNumber;
    }

    /**
     * Returns the active round phase.
     *
     * @return current round phase
     */
    public RoundPhase getCurrentRoundPhase() {
        return this.currentRoundPhase;
    }

    /**
     * Returns a copy of the current phase order.
     *
     * <p>This is the order used only by the active round phase. It can be
     * replaced when the game moves to another phase.
     *
     * @return ordered players for the current phase
     */
    public List<Player> getCurrentPhaseOrder() {
        this.ensureCurrentPhaseOrderInitialized();
        return new ArrayList<>(this.currentPhaseOrder);
    }

    /**
     * Returns the index of the current player inside the current phase order.
     *
     * @return current player index
     */
    public int getCurrentPlayerIndex() {
        this.ensureCurrentPhaseOrderInitialized();
        return this.currentPlayerIndex;
    }

    /**
     * Replaces the current phase order and moves the cursor to the first player.
     *
     * <p>This method is intentionally used more than once during a round. For
     * example, after all totems have been placed, the game replaces the totem
     * placement order with the card draw order.
     *
     * @param newOrder new ordered list of players
     */
    public void setCurrentPhaseOrder(List<Player> newOrder) {
        this.validateCurrentPhaseOrder(newOrder);
        this.currentPhaseOrder = new ArrayList<>(newOrder);
        this.currentPlayerIndex = 0;
    }

    /**
     * Changes the current phase of the round.
     *
     * @param currentRoundPhase new round phase
     */
    public void setCurrentRoundPhase(RoundPhase currentRoundPhase) {
        this.currentRoundPhase = currentRoundPhase;
    }

    /**
     * Moves the cursor to the next player.
     */
    public void advanceToNextPlayer() {
        this.ensureCurrentPhaseOrderInitialized();
        if (this.isLastPlayer()) {
            throw new IllegalStateException("Cannot advance beyond the last player of the round");
        }

        this.currentPlayerIndex = this.currentPlayerIndex + 1;
    }

    /**
     * Returns whether the current player is the last player in the current order.
     *
     * @return {@code true} if the current player is the last player
     */
    public boolean isLastPlayer() {
        this.ensureCurrentPhaseOrderInitialized();
        return this.currentPlayerIndex == this.currentPhaseOrder.size() - 1;
    }

    /**
     * Increments the round number by one.
     */
    public void advanceRoundNumber() {
        this.roundNumber = this.roundNumber + 1;
    }

    /**
     * Checks that a phase order can actually be used.
     *
     * <p>A round phase with no players has no valid current player. For this
     * reason every assigned order must be non-null and non-empty.
     *
     * @param currentPhaseOrder order to validate
     */
    private void validateCurrentPhaseOrder(List<Player> currentPhaseOrder) {
        if (currentPhaseOrder == null || currentPhaseOrder.isEmpty()) {
            throw new IllegalArgumentException("Current phase order cannot be null or empty");
        }
    }

    /**
     * Checks that the current-player cursor points inside the phase order.
     *
     * <p>The index must always identify an existing player in the current phase
     * order.
     *
     * @param currentPhaseOrder order containing the current player
     * @param currentPlayerIndex index to validate
     */
    private void validateCurrentPlayerIndex(List<Player> currentPhaseOrder, int currentPlayerIndex) {
        if (currentPlayerIndex < 0 || currentPlayerIndex >= currentPhaseOrder.size()) {
            throw new IllegalArgumentException("Current player index is out of bounds");
        }
    }

    /**
     * Ensures that the round flow already has a real player order.
     *
     * <p>The default constructor intentionally starts with an empty order because
     * setup has not run yet. Methods that need a current player must fail fast
     * if they are called before setup provides the first real order.
     */
    private void ensureCurrentPhaseOrderInitialized() {
        if (this.currentPhaseOrder.isEmpty()) {
            throw new IllegalStateException("Current phase order has not been initialized");
        }
    }
}
