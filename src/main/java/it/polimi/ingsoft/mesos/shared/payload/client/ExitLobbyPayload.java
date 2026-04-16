package it.polimi.ingsoft.mesos.shared.payload.client;
import java.io.Serializable;
public class ExitLobbyPayload implements Serializable {
    public final int lobbyId;

    public ExitLobbyPayload(int lobbyId) {
        this.lobbyId = lobbyId;
    }
}
