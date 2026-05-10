package it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.PreLobbyServerPayload;
import java.io.Serializable;
import java.util.List;

public class LobbyListPayload implements Serializable, PreLobbyServerPayload {
    public final List<LobbySummaryPayload> lobbies;

    public LobbyListPayload(List<LobbySummaryPayload> lobbies) {
        this.lobbies = lobbies;
    }
}
