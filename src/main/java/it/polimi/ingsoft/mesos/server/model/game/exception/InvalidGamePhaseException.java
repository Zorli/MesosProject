package it.polimi.ingsoft.mesos.server.model.game.exception;

import it.polimi.ingsoft.mesos.server.model.game.enums.RoundPhase;

/**
 * Signals that a requested action is not compatible with the current round or game phase.
 */
public class InvalidGamePhaseException extends IllegalStateException {

    /**
     * Creates the exception for an action that is incompatible with the current round phase.
     *
     * @param actionName attempted action name
     * @param currentPhase current round or game phase
     */
    public InvalidGamePhaseException(String actionName, RoundPhase currentPhase) {
        super("Action " + actionName + " is not allowed during round phase " + currentPhase);
    }
}
