package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.shared.payload.client.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;

public enum LobbyCommandType {
    START_LOBBY,
    EXIT_LOBBY;

    public static LobbyCommandType fromPayload(ClientPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        return switch (payload) {
            case StartLobbyPayload ignored -> START_LOBBY;
            case ExitLobbyPayload ignored -> EXIT_LOBBY;
            default -> throw new IllegalArgumentException("Unexpected lobby payload: " + payload);
        };
    }
}
