package it.polimi.ingsoft.mesos.server.controller.ranking;

/**
 * Signals that a match identified by the same game id has already been stored.
 */
public class DuplicateGameException extends IllegalStateException {

    /**
     * Builds the exception with a message containing the duplicate game id.
     *
     * @param gameId unique match id that was already persisted
     */
    public DuplicateGameException(String gameId) {
        super("Game already recorded: " + gameId);
    }
}
