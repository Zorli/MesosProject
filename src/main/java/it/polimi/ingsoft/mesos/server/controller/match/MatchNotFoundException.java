package it.polimi.ingsoft.mesos.server.controller.match;

/**
 * Raised when a caller requires an active match that is not registered.
 */
public final class MatchNotFoundException extends IllegalArgumentException {
    /**
     * Creates the exception for the missing match identifier.
     *
     * @param gameId missing game identifier
     */
    public MatchNotFoundException(int gameId) {
        super("Match not found: " + gameId);
    }
}
