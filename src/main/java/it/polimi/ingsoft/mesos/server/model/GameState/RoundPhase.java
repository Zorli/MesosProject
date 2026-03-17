package it.polimi.ingsoft.mesos.server.model.GameState;

/**
 * High-level round lifecycle for a game in progress.
 *
 * <p>The value controls which round-scoped action is currently legal in {@link Game}, from normal
 * player turns through end-of-round transitions and final scoring.
 */
public enum RoundPhase {
    PLACING_TOTEMS,
    PLAYER_TURNS,
    RESOLVING_EVENTS,
    RETURNING_TO_START,
    RESETTING_BOARD,
    POST_REFILL_DRAW,
    FINAL_SCORING
}
