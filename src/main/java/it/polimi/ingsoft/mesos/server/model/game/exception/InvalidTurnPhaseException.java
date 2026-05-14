package it.polimi.ingsoft.mesos.server.model.game.exception;

import it.polimi.ingsoft.mesos.server.model.game.enums.DraftTurnState;

/**
 * Signals that a requested action is not compatible with the current turn phase.
 */
public class InvalidTurnPhaseException extends IllegalStateException {

    /**
     * Creates the exception for an action that is incompatible with the current draft turn state.
     *
     * @param actionName attempted action name
     * @param currentPhase current turn phase
     */
    public InvalidTurnPhaseException(String actionName, DraftTurnState currentPhase) {
        super("Action " + actionName + " is not allowed during phase " + currentPhase);
    }
}
