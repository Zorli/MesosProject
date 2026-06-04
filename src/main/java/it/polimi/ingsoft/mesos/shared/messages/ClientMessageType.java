package it.polimi.ingsoft.mesos.shared.messages;

/**
 * Defines the set of possible message types that a client can send to the server.
 * <p>
 * These types match the different client commands and network requests sent during
 * connection, pre-lobby, lobby, and active game phases.
 */
public enum ClientMessageType {
    /** Request to authenticate/log in a player using a nickname. */
    AUTHENTICATE,
    /** Request to list all currently open/active lobbies on the server. */
    LIST_LOBBIES,
    /** Request to create a new game lobby with a specified max player count. */
    CREATE_LOBBY,
    /** Request to join an existing lobby by its identifier. */
    JOIN_LOBBY,
    /** Request to exit the current lobby. */
    EXIT_LOBBY,
    /** Request by the lobby host to start the game. */
    START_LOBBY,
    /** Request to move a player's totem on the game board. */
    MOVE_TOTEM,
    /** Request to choose a card from the draft offer during game. */
    CHOOSE_CARD,
    /** Request to perform an extra draw action during game. */
    EXTRA_DRAW,
    /** Keep-alive response confirming the client connection is active. */
    PONG,
    SKIP_EXTRA_DRAW
}
