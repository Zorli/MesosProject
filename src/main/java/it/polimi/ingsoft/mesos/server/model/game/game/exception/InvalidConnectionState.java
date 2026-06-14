package it.polimi.ingsoft.mesos.server.model.game.game.exception;

/**
 * Signals that a game action cannot proceed because the players' connection
 * state does not allow it.
 *
 * <p>It is thrown by the game model's "only one player connected" guard: when a
 * single participant remains connected, normal actions are rejected so that one
 * player cannot keep advancing the match while the others are unable to react.
 */
public class InvalidConnectionState extends IllegalStateException{
    /**
     * Creates the exception with a detail describing the invalid connection state.
     * The message is prefixed with {@code "Invalid connection state: "}.
     *
     * @param message specific explanation of why the connection state is invalid
     */
    public InvalidConnectionState(String message) {
        super("Invalid connection state: " + message);
    }
}
