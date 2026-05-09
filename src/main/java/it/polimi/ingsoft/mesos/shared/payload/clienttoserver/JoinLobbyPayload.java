package it.polimi.ingsoft.mesos.shared.payload.clienttoserver;
import java.io.Serializable;
public class JoinLobbyPayload implements Serializable,ClientPayload {
    public final int lobbyId;

    public JoinLobbyPayload(int lobbyId) {
        this.lobbyId = lobbyId;
    }
}
