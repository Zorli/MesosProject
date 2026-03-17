package it.polimi.ingsoft.mesos.server.model.enums;

/**
 * Enumerates the fine-grained phases inside a single player's draw turn.
 */
public enum TurnPhase {
    /** The player may choose one or more cards allowed by the reached space. */
    WAITING_FOR_CARD_SELECTION,
    /** All mandatory actions for the turn have been completed. */
    TURN_COMPLETED
}
