package it.polimi.ingsoft.mesos.server.model.game.game.exception;

/**
 * Exception raised when a game command is incompatible with the connection state
 * of the player session.
 * <p>
 * In the game layer, some operations depend not only on the game phase, but also
 * on whether the player is connected, disconnected, or reconnecting. This
 * exception marks that the command cannot be applied because the session state
 * does not satisfy that requirement.
 */
public class InvalidConnectionState extends IllegalStateException{
    /**
     * Creates an invalid connection state exception.
     *
     * @param message specific explanation of the invalid session state
     */
    public InvalidConnectionState(String message) {
        super("Invalid connection state: " + message);
    }
}
