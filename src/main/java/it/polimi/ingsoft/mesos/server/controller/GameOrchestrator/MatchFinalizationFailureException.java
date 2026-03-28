package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

/**
 * Raised when final scoring succeeds in memory but the application-level completion flow cannot
 * finish coherently.
 *
 * <p>The finalization flow includes the durable completed-match snapshot and any downstream
 * post-match projections such as ranking registration.
 */
public final class MatchFinalizationFailureException extends IllegalStateException {
    /**
     * Creates the exception describing a failed completed-match finalization.
     *
     * @param cause original save or ranking failure
     */
    public MatchFinalizationFailureException(Throwable cause) {
        super("Failed to finalize completed match; match is now frozen", cause);
    }
}
