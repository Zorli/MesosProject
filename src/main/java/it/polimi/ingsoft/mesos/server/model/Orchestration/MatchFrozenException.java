package it.polimi.ingsoft.mesos.server.model.Orchestration;

/**
 * Raised when a match has been frozen after a persistence failure and can no longer accept gameplay commands.
 */
public final class MatchFrozenException extends IllegalStateException {
    /**
     * Creates the exception with a fixed explanatory message.
     */
    public MatchFrozenException() {
        super("Match is frozen because the last durable save failed");
    }
}
