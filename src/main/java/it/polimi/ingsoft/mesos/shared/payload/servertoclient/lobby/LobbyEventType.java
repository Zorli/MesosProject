package it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby;

/**
 * Defines the type of events/changes that can occur inside a lobby room.
 * <p>
 * These events trigger lobby room updates sent to the connected lobby members.
 */
public enum LobbyEventType {
    /** The lobby has been successfully created. */
    LOBBY_CREATED,
    /** A player has joined the lobby. */
    LOBBY_JOINED,
    /** A player has left the lobby. */
    LOBBY_LEFT,
    /** The host has initiated starting the game. */
    LOBBY_STARTED
}
