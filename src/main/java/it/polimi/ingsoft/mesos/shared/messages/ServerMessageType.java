package it.polimi.ingsoft.mesos.shared.messages;

/**
 * Defines the set of possible message types that the preLobby can send to a client.
 * <p>
 * These types match the different responses, error messages, and state update
 * notifications sent from the preLobby to keep the client's local view in sync.
 */
public enum ServerMessageType {
    /** An error message payload (e.g. invalid action, out of turn, phase mismatch). */
    ERROR,
    /** The result of an authentication attempt. */
    AUTHENTICATION_RESULT,
    /** An update describing changes in the player's lobby room. */
    LOBBY_UPDATE,
    /** A list of open game lobbies currently available on the preLobby. */
    LOBBY_LIST,
    /** An update describing changes to the active game board or state. */
    GAME_UPDATE,
    /** Keep-alive ping query sent to the client to verify connection health. */
    PING
}
