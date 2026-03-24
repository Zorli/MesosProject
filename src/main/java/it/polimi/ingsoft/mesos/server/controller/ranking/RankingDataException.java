package it.polimi.ingsoft.mesos.server.controller.ranking;

/**
 * Wraps failures caused by inconsistent ranking state or unrecoverable persistence errors.
 */
public class RankingDataException extends IllegalStateException {

    /**
     * Builds the exception with a domain specific message.
     *
     * @param message human-readable error message
     */
    public RankingDataException(String message) {
        super(message);
    }
}
