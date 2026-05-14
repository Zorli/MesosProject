package it.polimi.ingsoft.mesos.server.model.game.game.exception;

import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;

/**
 * Signals that a requested action is not compatible with the current lifecycle status of the game.
 */
public class InvalidGameStatusException extends IllegalStateException {

    /**
     * Creates the exception for an action that is incompatible with the current game status.
     *
     * @param actionName attempted action name
     * @param currentStatus current game lifecycle status
     */
    public InvalidGameStatusException(String actionName, GameStatus currentStatus) {
        super("Action " + actionName + " is not allowed when game status is " + currentStatus);
    }
}
