package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;

/**
 * Signals that a requested action is not compatible with the current turn phase.
 */
public class InvalidTurnPhaseException extends IllegalStateException {

    public InvalidTurnPhaseException(String actionName, TurnPhase currentPhase) {
        super("Action " + actionName + " is not allowed during phase " + currentPhase);
    }
}
