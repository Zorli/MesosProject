package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

/**
 * Raised when a persisted match snapshot violates invariants required for exact restore.
 */
public final class SavedMatchSnapshotConsistencyException extends RuntimeException {
    /**
     * Creates the exception with a descriptive error message.
     *
     * @param message failure description
     */
    public SavedMatchSnapshotConsistencyException(String message) {
        super(message);
    }
}
