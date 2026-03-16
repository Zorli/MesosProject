package it.polimi.ingsoft.mesos.server.model.enums;

/**
 * Enumerates the high-level phases that compose a full round.
 */
public enum RoundPhase {
    /** Players take their normal turns. */
    PLAYER_TURN,
    /** Lower-row event cards are resolved. */
    LOWER_ROW_EVENTS,
    /** Totems are moved back to the starting tile. */
    RETURN_TO_STARTING_TILE,
    /** Starting-tile effects are resolved after players return. */
    STARTING_TILE_EFFECTS,
    /** Expired cards are removed from the visible rows. */
    ROW_CLEANUP,
    /** Visible rows are refilled from the decks. */
    ROW_REFILL,
    /** The game advances to the next era when appropriate. */
    ERA_ADVANCEMENT
}
