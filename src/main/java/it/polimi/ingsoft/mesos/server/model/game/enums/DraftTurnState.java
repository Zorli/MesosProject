package it.polimi.ingsoft.mesos.server.model.game.enums;

/**
 * State of the standard card-drafting turn.
 *
 * <p>This state is intentionally smaller than the full round phase. The round
 * phase says what part of the round the game is in. This enum says only whether
 * the current standard card-drafting turn is active or completed.
 */
public enum DraftTurnState {
    /**
     * No standard card choice is currently active.
     */
    TURN_COMPLETED,

    /**
     * The current player may choose a standard card from the board.
     */
    WAITING_FOR_CARD_SELECTION
}
