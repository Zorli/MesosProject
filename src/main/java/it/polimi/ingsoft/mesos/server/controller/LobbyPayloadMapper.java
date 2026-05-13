package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.util.List;

public final class LobbyPayloadMapper {

    public LobbyUpdatePayload authenticated(String nickname) {
        return new LobbyUpdatePayload(
                LobbyEventType.AUTHENTICATED,
                nickname,
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        );
    }

    public LobbyUpdatePayload lobbyCreated(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_CREATED, nickname, "Lobby created successfully", lobby);
    }

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

    public LobbyListPayload lobbyList(List<Lobby> lobbies) {
        return new LobbyListPayload(lobbies.stream()
                .map(this::summary)
                .toList());
    }

    public LobbySummaryPayload summary(Lobby lobby) {
        return new LobbySummaryPayload(
                lobby.getId(),
                lobby.getHostNickname(),
                lobby.getPlayers().size(),
                lobby.getChosenCapacity(),
                lobby.getStatus().name()
        );
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
