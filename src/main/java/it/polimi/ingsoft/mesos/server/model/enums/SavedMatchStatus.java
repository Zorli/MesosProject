package it.polimi.ingsoft.mesos.server.model.enums;

/**
 * Describes whether a persisted match can still be resumed or is already final.
 */
public enum SavedMatchStatus {
    /** The match is still resumable and must be kept until retention expires. */
    IN_PROGRESS,
    /** The match reached its terminal state and should not be resumed. */
    COMPLETED
}
