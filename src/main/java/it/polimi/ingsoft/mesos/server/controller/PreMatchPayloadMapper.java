package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.util.List;

public final class PreMatchPayloadMapper {

    public PreMatchUpdatePayload authenticated(String nickname) {
        return new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                nickname,
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        );
    }

    public PreMatchUpdatePayload lobbyCreated(String nickname, Lobby lobby) {
        return this.fromLobby(PreMatchEventType.LOBBY_CREATED, nickname, "Lobby created successfully", lobby);
    }

    public PreMatchUpdatePayload lobbyJoined(String nickname, Lobby lobby) {
        return this.fromLobby(PreMatchEventType.LOBBY_JOINED, nickname, "Lobby joined successfully", lobby);
    }

    public PreMatchUpdatePayload lobbyLeft(String nickname, Lobby lobby, int lobbyId) {
        if (lobby == null) {
            return new PreMatchUpdatePayload(
                    PreMatchEventType.LOBBY_LEFT,
                    nickname,
                    "Lobby closed",
                    lobbyId,
                    List.of(),
                    null,
                    null
            );
        }

        return this.fromLobby(PreMatchEventType.LOBBY_LEFT, nickname, "Lobby left successfully", lobby);
    }

    public PreMatchUpdatePayload lobbyStarted(String nickname, Lobby lobby) {
        return this.fromLobby(PreMatchEventType.LOBBY_STARTED, nickname, "Match has started", lobby);
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

    private PreMatchUpdatePayload fromLobby(
            PreMatchEventType event,
            String nickname,
            String message,
            Lobby lobby
    ) {
        return new PreMatchUpdatePayload(
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
