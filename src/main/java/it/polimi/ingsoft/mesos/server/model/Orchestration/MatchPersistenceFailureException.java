package it.polimi.ingsoft.mesos.server.model.Orchestration;

/**
 * Raised when a domain mutation succeeds in memory but the corresponding durable checkpoint cannot be saved.
 */
public final class MatchPersistenceFailureException extends IllegalStateException {
    /**
     * Creates the exception describing which checkpoint failed.
     *
     * @param checkpointName checkpoint that failed to persist
     * @param cause original persistence failure
     */
    public MatchPersistenceFailureException(String checkpointName, Throwable cause) {
        super("Failed to persist checkpoint " + checkpointName + "; match is now frozen", cause);
    }
}
