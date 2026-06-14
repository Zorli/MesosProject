package it.polimi.ingsoft.mesos.server.controller.game.exceptions;

/**
 * Raised when a match has been frozen and can no longer accept gameplay commands.
 */
public final class MatchFrozenException extends IllegalStateException {
    /**
     * Creates the exception with a fixed explanatory message.
     */
    public MatchFrozenException() {
        super("Match is frozen");
    }
}
