package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

/**
 * Identifies the application checkpoint that produced the currently persisted match state.
 *
 * <p>{@link #MATCH_COMPLETED} is the durable checkpoint for the final-scoring boundary. It is used
 * both when the match enters {@code FINAL_SCORING} and when the final scored snapshot is persisted
 * after {@code GameStatus.FINISHED}. The disambiguating information lives in the persisted game
 * status and round phase, not in a separate checkpoint enum value.
 */
public enum SavedMatchCheckpointType {
    GAME_INITIALIZED,
    TURN_STARTED,
    ERA_CHANGED,
    TURN_FINISHED,
    MATCH_COMPLETED
}
