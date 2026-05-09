package it.polimi.ingsoft.mesos.shared.payload.servertoclient;
import java.io.Serializable;
import java.util.List;

public class PreMatchUpdatePayload implements Serializable {
    public final PreMatchEventType event;
    public final String nickname;
    public final String message;
    public final Integer lobbyId;
    public final List<String> players;
    public final String hostNickname;
    public final Integer maxPlayers;

    public PreMatchUpdatePayload(
            PreMatchEventType event,
            String nickname,
            String message,
            Integer lobbyId,
            List<String> players,
            String hostNickname,
            Integer maxPlayers
    ) {
        this.event = event;
        this.nickname = nickname;
        this.message = message;
        this.lobbyId = lobbyId;
        this.players = players;
        this.hostNickname = hostNickname;
        this.maxPlayers = maxPlayers;
    }
}
