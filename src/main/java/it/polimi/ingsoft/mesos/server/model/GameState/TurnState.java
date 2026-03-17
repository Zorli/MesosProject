package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;


/*
    This class is meant to be the FSM that check every step in the single player turn.
*/

/**
 * Finite-state machine that validates and tracks the phases of a single player's turn.
 *
 * <p>The state starts waiting for a totem move, transitions to card selection once the player is
 * positioned on the offer path, tracks how many upper and lower row choices remain, and marks the
 * turn as completed when all mandatory selections have been consumed.
 */
public class TurnState {
    private TurnPhase phase;
    private Player activePlayer;
    private final Map<Player, Integer> remainingUpperChoices;
    private final Map<Player, Integer> remainingLowerChoices;

    /**
     * Creates a new turn state positioned at the beginning of a player's turn.
     */
    public TurnState() {
        this.phase = TurnPhase.WAITING_FOR_TOTEM_MOVE;
        this.activePlayer = null;
        this.remainingUpperChoices = new HashMap<>();
        this.remainingLowerChoices = new HashMap<>();
    }

    /**
     * Returns the current phase of the turn state machine.
     *
     * @return current turn phase
     */
    public TurnPhase getPhase() {
        return this.phase;
    }


    /**
     * Returns whether the FSM is currently waiting for a totem placement.
     *
     * @return {@code true} when a player can place the totem
     */
    public boolean canMoveTotem() {
        return this.phase == TurnPhase.WAITING_FOR_TOTEM_MOVE;
    }

    /**
     * Checks whether an upper-row card can currently be chosen.
     *
     * @return {@code true} when the turn is in card-selection phase and at least one upper choice remains
     */
    public boolean canChooseFromUpperRow() {
        return this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION
                && this.activePlayer != null
                && this.getRemainingUpperChoices(this.activePlayer) > 0;
    }

    /**
     * Checks whether a lower-row card can currently be chosen.
     *
     * @return {@code true} when the turn is in card-selection phase and at least one lower choice remains
     */
    public boolean canChooseFromLowerRow() {
        return this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION
                && this.activePlayer != null
                && this.getRemainingLowerChoices(this.activePlayer) > 0;
    }

    /**
     * Returns whether the active player is currently expected to place a totem.
     *
     * @return {@code true} when the FSM expects a totem move
     */
    public boolean isWaitingForTotemMove() {
        return this.phase == TurnPhase.WAITING_FOR_TOTEM_MOVE;
    }

    /**
     * Returns whether the active player is currently in the card-selection phase.
     *
     * @return {@code true} when the FSM expects a card choice
     */
    public boolean isWaitingForCardSelection() {
        return this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION;
    }

    /**
     * Records the result of a valid totem move and enters the card-selection phase.
     *
     * @param player player whose resolved offer-space choices are being registered
     * @param upperChoices number of upper-row cards that may be chosen
     * @param lowerChoices number of lower-row cards that may be chosen
     */
    public void onTotemMoved(Player player, int upperChoices, int lowerChoices) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (upperChoices < 0 || lowerChoices < 0) {
            throw new IllegalArgumentException("Choices must be positive numbers");
        }
        if (!this.canMoveTotem()) {
            throw new IllegalStateException("Cannot register drawable cards during phase " + this.phase);
        }
        this.remainingUpperChoices.put(player, upperChoices);
        this.remainingLowerChoices.put(player, lowerChoices);
    }

    /**
     * Starts the card-selection sub-phase for the specified player.
     *
     * @param player player that must now choose cards
     */
    public void startCardSelection(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (!this.remainingUpperChoices.containsKey(player) || !this.remainingLowerChoices.containsKey(player)) {
            throw new IllegalStateException("Drawable cards were not registered for player " + player.getNickname());
        }

        this.activePlayer = player;
        if (this.getRemainingUpperChoices(player) == 0 && this.getRemainingLowerChoices(player) == 0) {
            this.phase = TurnPhase.TURN_COMPLETED;
            return;
        }
        this.phase = TurnPhase.WAITING_FOR_CARD_SELECTION;
    }

    /**
     * Consumes one available upper-row choice and updates the phase if the turn is finished.
     */
    public void onUpperCardChosen() {
        if (!this.canChooseFromUpperRow()) {
            throw new IllegalStateException("Cannot choose from upper row");
        }

        this.remainingUpperChoices.put(this.activePlayer, this.getRemainingUpperChoices(this.activePlayer) - 1);
        this.updatePhaseAfterCardChoice();
    }

    /**
     * Consumes one available lower-row choice and updates the phase if the turn is finished.
     */
    public void onLowerCardChosen() {
        if (!this.canChooseFromLowerRow()) {
            throw new IllegalStateException("Cannot take card from lower row");
        }

        this.remainingLowerChoices.put(this.activePlayer, this.getRemainingLowerChoices(this.activePlayer) - 1);
        this.updatePhaseAfterCardChoice();
    }

    /**
     * Returns whether the current player has completed all mandatory actions for the turn.
     *
     * @return {@code true} when the FSM reached {@link TurnPhase#TURN_COMPLETED}
     */
    public boolean isTurnCompleted() {
        return this.phase == TurnPhase.TURN_COMPLETED;
    }

    /**
     * Clears the completed player's stored choices and prepares card selection for the next player.
     */
    public void resetForNextPlayer() {
        if (this.phase != TurnPhase.TURN_COMPLETED) {
            throw new IllegalStateException("Cannot reset for next player until the current one has finished");
        }
        if (this.activePlayer != null) {
            this.remainingUpperChoices.remove(this.activePlayer);
            this.remainingLowerChoices.remove(this.activePlayer);
        }
        this.activePlayer = null;
        this.phase = TurnPhase.WAITING_FOR_CARD_SELECTION;
    }

    /**
     * Clears every stored per-player choice and returns to the totem-placement phase.
     */
    public void resetForNewRound() {
        this.remainingUpperChoices.clear();
        this.remainingLowerChoices.clear();
        this.activePlayer = null;
        this.phase = TurnPhase.WAITING_FOR_TOTEM_MOVE;
    }

    /**
     * Returns how many upper-row picks are still available to the active player.
     *
     * @return remaining upper-row choices
     */
    public int getRemainingUpperChoices() {
        if (this.activePlayer == null) {
            return 0;
        }
        return this.getRemainingUpperChoices(this.activePlayer);
    }

    /**
     * Returns the remaining upper-row picks stored for the specified player.
     *
     * @param player player whose upper-row picks are requested
     * @return remaining upper-row choices
     */
    public int getRemainingUpperChoices(Player player) {
        return this.remainingUpperChoices.getOrDefault(player, 0);
    }

    /**
     * Returns how many lower-row picks are still available to the active player.
     *
     * @return remaining lower-row choices
     */
    public int getRemainingLowerChoices() {
        if (this.activePlayer == null) {
            return 0;
        }
        return this.getRemainingLowerChoices(this.activePlayer);
    }

    /**
     * Returns the remaining lower-row picks stored for the specified player.
     *
     * @param player player whose lower-row picks are requested
     * @return remaining lower-row choices
     */
    public int getRemainingLowerChoices(Player player) {
        return this.remainingLowerChoices.getOrDefault(player, 0);
    }

    /**
     * Marks the turn as completed once both upper and lower choice counters reach zero.
     */
    private void updatePhaseAfterCardChoice() {
        if (this.activePlayer != null
                && this.getRemainingUpperChoices(this.activePlayer) == 0
                && this.getRemainingLowerChoices(this.activePlayer) == 0) {
            this.phase = TurnPhase.TURN_COMPLETED;
        }
    }

}
