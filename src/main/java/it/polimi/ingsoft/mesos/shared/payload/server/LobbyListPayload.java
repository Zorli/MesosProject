package it.polimi.ingsoft.mesos.shared.payload.server;
import java.io.Serializable;
import java.util.List;

public class LobbyListPayload implements Serializable {
    public final List<LobbySummaryPayload> lobbies;

    public LobbyListPayload(List<LobbySummaryPayload> lobbies) {
        this.lobbies = lobbies;
    }
}
