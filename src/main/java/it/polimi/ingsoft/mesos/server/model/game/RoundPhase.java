package it.polimi.ingsoft.mesos.server.model.game;

/**
 * High-level round lifecycle for a game in progress.
 *
 * <p>The value controls which round-scoped action is currently legal in {@link Game}, from normal
 * player turns through end-of-round transitions and final scoring.
 */
public enum RoundPhase {
    PLACING_TOTEMS,
    PLAYER_DRAWS,
    EXTRA_DRAWS,
    RESOLVING_EFFECTS,
    RESETTING_BOARD,
    FINAL_SCORING
}
