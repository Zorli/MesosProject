package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.util.List;

public final class LobbyPayloadMapper {


    public LobbyUpdatePayload lobbyJoined(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_JOINED, nickname, "Lobby joined successfully", lobby);
    }

    public LobbyUpdatePayload lobbyLeft(String nickname, Lobby lobby, int lobbyId) {
        if (lobby == null) {
            return new LobbyUpdatePayload(
                    LobbyEventType.LOBBY_LEFT,
                    nickname,
                    "Lobby closed",
                    lobbyId,
                    List.of(),
                    null,
                    null
            );
        }

        return this.fromLobby(LobbyEventType.LOBBY_LEFT, nickname, "Lobby left successfully", lobby);
    }

    public LobbyUpdatePayload lobbyStarted(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_STARTED, nickname, "Match has started", lobby);
    }


    private LobbyUpdatePayload fromLobby(
            LobbyEventType event,
            String nickname,
            String message,
            Lobby lobby
    ) {
        return new LobbyUpdatePayload(
                event,
                nickname,
                message,
                lobby.getId(),
                lobby.getPlayers(),
                lobby.getHostNickname(),
                lobby.getChosenCapacity()
        );
    }
}
