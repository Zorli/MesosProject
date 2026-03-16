package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;


/*
    This class is meant to be the FSM that check every step in the single player turn.
*/
//TODO la posizione sulle pedine è quella di iscrizione in lobby

/**
 * Finite-state machine that validates and tracks the phases of a single player's turn.
 *
 * <p>The state starts waiting for a totem move, transitions to card selection once the player is
 * positioned on the offer path, tracks how many upper and lower row choices remain, and marks the
 * turn as completed when all mandatory selections have been consumed.
 */
public class TurnState {
    private TurnPhase phase;
    //If this player has the building that gives the perk to take another card, how we handle it?
    //At this point is the offer path that prescribes how much card the player can take
    private int remainingUpperChoices;
    private int remainingLowerChoices;

    /**
     * Creates a new turn state positioned at the beginning of a player's turn.
     */
    public TurnState() {
        this.phase = TurnPhase.WAITING_FOR_TOTEM_MOVE;
        this.remainingUpperChoices = 0;
        this.remainingLowerChoices = 0;
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
     * Checks whether the player is currently allowed to move the totem.
     *
     * @return {@code true} when the FSM is waiting for the totem move
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
                && this.remainingUpperChoices > 0;
    }

    /**
     * Checks whether a lower-row card can currently be chosen.
     *
     * @return {@code true} when the turn is in card-selection phase and at least one lower choice remains
     */
    public boolean canChooseFromLowerRow() {
        return this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION
                && this.remainingLowerChoices > 0;
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
     * @param upperChoices number of upper-row cards that may be chosen
     * @param lowerChoices number of lower-row cards that may be chosen
     */
    public void onTotemMoved(int upperChoices, int lowerChoices) {
        if (!canMoveTotem()) {
            throw new IllegalStateException("Totem move is not allowed in this phase ");
        }

        if (upperChoices < 0 || lowerChoices < 0) {
            throw new IllegalArgumentException("Must Be positive numbers");
        }
        this.phase = TurnPhase.WAITING_FOR_CARD_SELECTION;
        this.remainingUpperChoices = upperChoices;
        this.remainingLowerChoices = lowerChoices;
    }

    /**
     * Consumes one available upper-row choice and updates the phase if the turn is finished.
     */
    public void onUpperCardChosen() {
        if (!canChooseFromUpperRow()) {
            throw new IllegalStateException("Cannot choose from upper row");
        }

        this.remainingUpperChoices -= 1;
        this.updatePhaseAfterCardChoice();
    }

    /**
     * Consumes one available lower-row choice and updates the phase if the turn is finished.
     */
    public void onLowerCardChosen() {
        if (!canChooseFromLowerRow()) {
            throw new IllegalStateException("Cannot take card from lower row");
        }

        this.remainingLowerChoices -= 1;
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
     * Resets the FSM so that the next player starts from the totem-move phase.
     */
    public void resetForNextPlayer() {
        if(!(this.phase == TurnPhase.TURN_COMPLETED)){throw  new IllegalStateException("Cannot reset for next player untile the current one hase finished");}
        this.phase = TurnPhase.WAITING_FOR_TOTEM_MOVE;
        this.remainingUpperChoices = 0;
        this.remainingLowerChoices = 0;
    }

    /**
     * Returns how many upper-row picks are still available to the active player.
     *
     * @return remaining upper-row choices
     */
    public int getRemainingUpperChoices() {
        return this.remainingUpperChoices;
    }

    /**
     * Returns how many lower-row picks are still available to the active player.
     *
     * @return remaining lower-row choices
     */
    public int getRemainingLowerChoices() {
        return this.remainingLowerChoices;
    }

    /**
     * Marks the turn as completed once both upper and lower choice counters reach zero.
     */
    private void updatePhaseAfterCardChoice() {
        if (this.remainingUpperChoices == 0 && this.remainingLowerChoices
                == 0) {
            this.phase = TurnPhase.TURN_COMPLETED;
        }
    }
}
