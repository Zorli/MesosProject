package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListLobbiesPayload;

public enum PreLobbyCommandType {
    AUTHENTICATE,
    JOIN_LOBBY,
    CREATE_LOBBY,
    LIST_LOBBIES;

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
