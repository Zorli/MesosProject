package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

/**
 * Signals that the final ranking update failed after a match reached its finished state.
 */
public class RankingFinalizationFailureException extends IllegalStateException {
    /**
     * Creates the exception wrapping the ranking failure.
     *
     * @param cause original ranking failure
     */
    public RankingFinalizationFailureException(Throwable cause) {
        super("Cannot finalize match ranking", cause);
    }
}
