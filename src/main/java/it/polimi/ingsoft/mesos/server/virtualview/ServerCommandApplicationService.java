package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.PreMatchPayloadMapper;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.network.AuthenticationException;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListLobbiesPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;

import java.util.List;
import java.util.Objects;

/**
 * Application command boundary shared by TCP and RMI adapters.
 */
public final class ServerCommandApplicationService {
    private final ServerController serverController;
    private final String sessionId;
    private final PreMatchPayloadMapper payloadMapper;

    public ServerCommandApplicationService(ServerController serverController, String sessionId) {
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.payloadMapper = new PreMatchPayloadMapper();
    }

    public ServerMessage authenticate(AuthenticationPayload payload) {
        if (this.serverController.isAuthenticated(this.sessionId)) {
            throw new AuthenticationException("Client is already authenticated");
        }
        if (payload == null || payload.nickname == null || payload.nickname.isBlank()) {
            throw new IllegalArgumentException("Authentication payload must contain a nickname");
        }

        try {
            this.serverController.authenticate(this.sessionId, payload.nickname);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationException(exception.getMessage(), exception);
        }

        return new ServerMessage(
                ServerMessageType.PRE_MATCH_UPDATE,
                this.payloadMapper.authenticated(this.serverController.nicknameOf(this.sessionId))
        );
    }

    public ServerMessage listLobbies(ListLobbiesPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "List-lobbies payload cannot be null");
        List<LobbySummaryPayload> lobbies = this.serverController.listOpenLobbies().stream()
                .map(this::toLobbySummaryPayload)
                .toList();
        return new ServerMessage(ServerMessageType.LOBBY_LIST, new LobbyListPayload(lobbies));
    }

    public ServerMessage createLobby(CreateLobbyPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "Create-lobby payload cannot be null");
        this.serverController.createLobby(this.sessionId, payload.chosenCapacity);
        return null;
    }

    public ServerMessage joinLobby(JoinLobbyPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "Join-lobby payload cannot be null");
        if (payload.lobbyId <= 0) {
            throw new IllegalArgumentException("Join-lobby payload must contain a valid lobbyId");
        }
        this.serverController.joinLobby(this.sessionId, payload.lobbyId);
        return null;
    }

    public ServerMessage exitLobby(ExitLobbyPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "Exit-lobby payload cannot be null");
        this.serverController.exitLobby(this.sessionId);
        return null;
    }

    public ServerMessage startLobby(StartLobbyPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "Start-lobby payload cannot be null");
        this.serverController.startLobby(this.sessionId);
        return null;
    }

    public ServerMessage moveTotem(MoveTotemPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "Move-totem payload cannot be null");
        if (payload.tile == null || payload.tile.isBlank()) {
            throw new IllegalArgumentException("Move-totem payload must contain a tile");
        }
        String nickname = this.serverController.nicknameOf(this.sessionId);
        this.serverController.gameOf(this.sessionId).moveTotem(nickname, payload.tile);
        return null;
    }

    public ServerMessage chooseCard(ChooseCardPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "Choose-card payload cannot be null");
        String nickname = this.serverController.nicknameOf(this.sessionId);
        this.serverController.gameOf(this.sessionId).chooseCard(nickname, payload.cardId);
        return null;
    }

    public ServerMessage extraDraw(ExtraDrawPayload payload) {
        this.ensureAuthenticated();
        this.requirePayload(payload, "Extra-draw payload cannot be null");
        String nickname = this.serverController.nicknameOf(this.sessionId);
        this.serverController.gameOf(this.sessionId).extraDrawn(nickname, payload.cardId);
        return null;
    }

    private void ensureAuthenticated() {
        if (!this.serverController.isAuthenticated(this.sessionId)) {
            throw new AuthenticationException("Client is not authenticated");
        }
    }

    private void requirePayload(Object payload, String message) {
        if (payload == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private LobbySummaryPayload toLobbySummaryPayload(Lobby lobby) {
        return new LobbySummaryPayload(
                lobby.getId(),
                lobby.getHostNickname(),
                lobby.getPlayers().size(),
                lobby.getChosenCapacity(),
                lobby.getStatus().name()
        );
    }
}
