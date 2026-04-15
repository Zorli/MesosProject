package it.polimi.ingsoft.mesos.shared.payload.server;

import java.util.List;

public class LobbyListPayload {
    public final List<LobbySummaryPayload> lobbies;

    public LobbyListPayload(List<LobbySummaryPayload> lobbies) {
        this.lobbies = lobbies;
    }
}
