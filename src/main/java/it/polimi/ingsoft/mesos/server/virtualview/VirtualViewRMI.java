package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
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
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Dedicated RMI remote session for a single client.
 */
public class VirtualViewRMI extends UnicastRemoteObject implements RmiServerService, Listener, PingPongConnection {
    private final ServerController serverController;
    private final RmiClientService clientCallback;
    private final String sessionId;
    private final ServerCommandApplicationService applicationService;
    private final ApplicationErrorMapper errorMapper;

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback) throws RemoteException {
        super();
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.sessionId = this.serverController.registerConnection(this);
        this.applicationService = new ServerCommandApplicationService(this.serverController, this.sessionId);
        this.errorMapper = new ApplicationErrorMapper();
    }

    public String getSessionId() {
        return this.sessionId;
    }

    @Override
    public void sendPing() {
        try {
            this.clientCallback.handlePing();
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void authenticate(AuthenticationPayload payload) throws RemoteException {
        this.execute("AUTHENTICATE", payload == null ? "null" : "nickname=" + payload.nickname,
                () -> this.applicationService.authenticate(payload));
    }

    @Override
    public void joinLobby(JoinLobbyPayload payload) throws RemoteException {
        this.execute("JOIN_LOBBY", payload == null ? "null" : "lobbyId=" + payload.lobbyId,
                () -> this.applicationService.joinLobby(payload));
    }

    @Override
    public void createLobby(CreateLobbyPayload payload) throws RemoteException {
        this.execute("CREATE_LOBBY", payload == null ? "null" : "chosenCapacity=" + payload.chosenCapacity,
                () -> this.applicationService.createLobby(payload));
    }

    @Override
    public void exitLobby(ExitLobbyPayload payload) throws RemoteException {
        this.execute("EXIT_LOBBY", "{}", () -> this.applicationService.exitLobby(payload));
    }

    @Override
    public void listLobbies(ListLobbiesPayload payload) throws RemoteException {
        this.execute("LIST_LOBBIES", "{}", () -> this.applicationService.listLobbies(payload));
    }

    @Override
    public void startLobby(StartLobbyPayload payload) throws RemoteException {
        this.execute("START_LOBBY", "{}", () -> this.applicationService.startLobby(payload));
    }

    @Override
    public void moveTotem(MoveTotemPayload payload) throws RemoteException {
        this.execute("MOVE_TOTEM", payload == null ? "null" : "tile=" + payload.tile,
                () -> this.applicationService.moveTotem(payload));
    }

    @Override
    public void chooseCard(ChooseCardPayload payload) throws RemoteException {
        this.execute("CHOOSE_CARD", payload == null ? "null" : "cardId=" + payload.cardId,
                () -> this.applicationService.chooseCard(payload));
    }

    @Override
    public void extraDraw(ExtraDrawPayload payload) throws RemoteException {
        this.execute("EXTRA_DRAW", payload == null ? "null" : "cardId=" + payload.cardId,
                () -> this.applicationService.extraDraw(payload));
    }

    @Override
    public void pong(PongPayload payload) throws RemoteException {
        if (payload == null) {
            this.clientCallback.handleError(this.errorMapper.toPayload(
                    new IllegalArgumentException("Malformed pong payload")
            ));
            return;
        }
        this.recordPong();
    }

    @Override
    public void closeSession() throws RemoteException {
        this.cleanup();
    }

    @Override
    public void close() {
        this.cleanup();
    }

    private void cleanup() {
        GameOrchestrator game = this.safeGame();
        if (game != null) {
            game.getGame().removeListener(this);
        }
        this.serverController.disconnectSession(this.sessionId);
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
        }
    }

    private void logReceived(String command, String payload) {
        System.out.println("[RMI] Received " + command
                + " from " + this.describeSession()
                + " payload=" + payload);
    }

    private void execute(String command, String payload, CommandInvocation invocation) throws RemoteException {
        try {
            this.logReceived(command, payload);
            this.dispatch(invocation.invoke());
        } catch (RuntimeException exception) {
            this.clientCallback.handleError(this.errorMapper.toPayload(exception));
        }
    }

    private void dispatch(ServerMessage message) throws RemoteException {
        if (message == null) {
            return;
        }
        if (message.type == ServerMessageType.PRE_MATCH_UPDATE && message.payload instanceof PreMatchUpdatePayload payload) {
            this.clientCallback.handlePreMatchUpdate(payload);
            return;
        }
        if (message.type == ServerMessageType.LOBBY_LIST && message.payload instanceof LobbyListPayload payload) {
            this.clientCallback.handleLobbyList(payload);
            return;
        }
        if (message.type == ServerMessageType.GAME_UPDATE && message.payload instanceof GamePayload payload) {
            this.clientCallback.handleGameUpdate(payload);
            return;
        }
        if (message.type == ServerMessageType.ERROR && message.payload instanceof ErrorPayload payload) {
            this.clientCallback.handleError(payload);
        }
    }

    @Override
    public void notifyUpdate(Object payload) {
        try {
            if (payload instanceof GamePayload gamePayload) {
                this.enterCurrentMatchIfAvailable();
                this.clientCallback.handleGameUpdate(gamePayload);
                return;
            }
            if (payload instanceof ErrorPayload errorPayload) {
                this.clientCallback.handleError(errorPayload);
                return;
            }
            if (payload instanceof PreMatchUpdatePayload preMatchUpdatePayload) {
                if (preMatchUpdatePayload.event == PreMatchEventType.LOBBY_STARTED) {
                    this.enterMatchIfAvailable(preMatchUpdatePayload.lobbyId);
                }
                this.clientCallback.handlePreMatchUpdate(preMatchUpdatePayload);
            }
        } catch (RemoteException ignored) {
        }
    }

    private void enterCurrentMatchIfAvailable() {
        Integer lobbyId = this.serverController.lobbyIdOf(this.sessionId);
        if (lobbyId != null) {
            this.enterMatchIfAvailable(lobbyId);
        }
    }

    private void enterMatchIfAvailable(int lobbyId) {
        try {
            this.serverController.enterGameIfAvailable(this.sessionId, lobbyId);
        } catch (Exception ignored) {
        }
    }

    public void recordPong() {
        this.serverController.recordPong(this.sessionId);
    }

    public boolean isAuthenticated() {
        return this.serverController.isAuthenticated(this.sessionId);
    }

    public String describeSession() {
        String nickname = this.serverController.nicknameOf(this.sessionId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "unauthenticated-client";
    }

    private GameOrchestrator safeGame() {
        try {
            return this.serverController.gameOf(this.sessionId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface CommandInvocation {
        ServerMessage invoke();
    }
}
