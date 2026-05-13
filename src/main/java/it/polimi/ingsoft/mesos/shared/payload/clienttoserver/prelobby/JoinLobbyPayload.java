package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import java.io.Serializable;
public class JoinLobbyPayload implements Serializable, PreLobbyClientPayload {
    public final int lobbyId;

    public JoinLobbyPayload(int lobbyId) {
        this.lobbyId = lobbyId;
    }
}
