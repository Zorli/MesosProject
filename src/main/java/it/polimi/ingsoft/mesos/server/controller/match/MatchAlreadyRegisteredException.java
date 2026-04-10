package it.polimi.ingsoft.mesos.server.controller.match;

/**
 * Raised when the active-match registry already contains a runtime for the same game id.
 */
public final class MatchAlreadyRegisteredException extends IllegalStateException {
    /**
     * Creates the exception for the duplicate match identifier.
     *
     * @param gameId duplicate game identifier
     */
    public MatchAlreadyRegisteredException(int gameId) {
        super("Match already registered: " + gameId);
    }
}
