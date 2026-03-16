package it.polimi.ingsoft.mesos.server.model.enums;

/**
 * Describes the lifecycle state of a lobby before and after match creation.
 */
public enum LobbyStatus {
    /** The lobby exists but does not yet have enough players to start. */
    WAITING,
    /** The lobby has enough players and can be turned into a game. */
    READY,
    /** A match has been created from the lobby. */
    STARTED,
    /** The lobby has been closed and should no longer accept players. */
    CLOSED
}
