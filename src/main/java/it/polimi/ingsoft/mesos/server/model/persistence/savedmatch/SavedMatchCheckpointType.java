package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

/**
 * Identifies the application checkpoint that produced the currently persisted match state.
 */
public enum SavedMatchCheckpointType {
    GAME_INITIALIZED,
    TURN_STARTED,
    ERA_CHANGED,
    TURN_FINISHED,
    MATCH_COMPLETED
}
