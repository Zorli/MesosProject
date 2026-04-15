package it.polimi.ingsoft.mesos.shared.payload.client;

public class ExitLobbyPayload {
    public final int lobbyId;

    public ExitLobbyPayload(int lobbyId) {
        this.lobbyId = lobbyId;
    }
}
