package it.polimi.ingsoft.mesos.server.controller.match;

/**
 * Raised when a command is submitted to a match runtime that has already been closed.
 */
public final class MatchRuntimeClosedException extends IllegalStateException {
    /**
     * Creates the exception for the given match identifier.
     *
     * @param gameId closed match identifier
     */
    public MatchRuntimeClosedException(int gameId) {
        super("Match runtime is closed for game " + gameId);
    }
}
