package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

/**
 * Raised when a player tries to attach to a saved match with a second concurrent session.
 */
public final class SavedMatchPlayerConnectionException extends RuntimeException {
    /**
     * Creates the exception with a descriptive error message.
     *
     * @param message failure description
     */
    public SavedMatchPlayerConnectionException(String message) {
        super(message);
    }
}
