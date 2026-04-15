package it.polimi.ingsoft.mesos.shared.payload.server;

public class LobbySummaryPayload {
    public final int lobbyId;
    public final String hostNickname;
    public final int currentPlayers;
    public final int maxPlayers;
    public final String status;

    public LobbySummaryPayload(int lobbyId, String hostNickname, int currentPlayers, int maxPlayers, String status) {
        this.lobbyId = lobbyId;
        this.hostNickname = hostNickname;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
    }
}
