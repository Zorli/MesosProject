package it.polimi.ingsoft.mesos.server.controller.preLobby.enums;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.ListLobbiesPayload;

/**
 * Enumeration representing the different actions/commands a client can perform
 * during the pre-lobby phase before entering a specific game lobby.
 */
public enum PreLobbyCommandType {
    /**
     * Authenticates a user session with a chosen nickname.
     */
    AUTHENTICATE,

    /**
     * Requests to join an existing game lobby.
     */
    JOIN_LOBBY,

    /**
     * Requests to create a new game lobby with a specified capacity.
     */
    CREATE_LOBBY,

    /**
     * Requests a list of currently open game lobbies.
     */
    LIST_LOBBIES;

    /**
     * Determines the PreLobbyCommandType associated with a given client payload.
     *
     * @param payload the client payload containing the request data
     * @return the matching PreLobbyCommandType
     * @throws IllegalArgumentException if the payload is null or its type is unrecognized
     */
    public static PreLobbyCommandType fromPayload(ClientPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        return switch (payload) {
            case AuthenticationPayload ignored -> AUTHENTICATE;
            case JoinLobbyPayload ignored -> JOIN_LOBBY;
            case CreateLobbyPayload ignored -> CREATE_LOBBY;
            case ListLobbiesPayload ignored -> LIST_LOBBIES;
            default -> throw new IllegalArgumentException("Unexpected pre-lobby payload: " + payload);
        };
    }
}
