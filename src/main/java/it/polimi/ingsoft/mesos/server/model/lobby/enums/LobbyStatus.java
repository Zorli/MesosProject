package it.polimi.ingsoft.mesos.server.model.lobby.enums;

/**
 * Describes the lifecycle state of a lobby before and after match creation.
 */
public enum LobbyStatus {
    /** The lobby exists but does not yet have enough players to start. */
    WAITING,
    /** The lobby represents a suspended match waiting for its original players to reconnect. */
    RESUMABLE,
    /** The lobby has enough players and can be turned into a game. */
    READY,
    /** A match has been created from the lobby. */
    STARTED,
    /** The lobby has been closed and should no longer accept players. */
    CLOSED
}
