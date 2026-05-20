package it.polimi.ingsoft.mesos.server.model.server;

import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;

import java.util.List;

public final class PreLobbyPayloadMapper {
    //Autenticazione
    public AuthenticationResultPayload authenticated(String nickname) {
        return new AuthenticationResultPayload(nickname, "Authentication completed successfully");
    }
    //Creazione Lobby
    public LobbyUpdatePayload lobbyCreated(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_CREATED, nickname, "Lobby created successfully", lobby);
    }
    //List lobbies
    public LobbyListPayload lobbyList(List<Lobby> lobbies) {
        return new LobbyListPayload(lobbies.stream()
                .map(this::summary)
                .toList());
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
    public LobbySummaryPayload summary(Lobby lobby) {
        return new LobbySummaryPayload(
                lobby.getId(),
                lobby.getHostNickname(),
                lobby.getPlayers().size(),
                lobby.getChosenCapacity(),
                lobby.getStatus().name()
        );
    }

}
