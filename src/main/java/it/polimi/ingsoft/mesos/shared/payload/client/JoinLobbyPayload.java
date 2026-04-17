package it.polimi.ingsoft.mesos.shared.payload.client;
import java.io.Serializable;
public class JoinLobbyPayload implements Serializable {
    public final int lobbyId;

    public JoinLobbyPayload(int lobbyId) {
        this.lobbyId = lobbyId;
    }
}
