package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.PreMatchPayloadMapper;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.*;
import it.polimi.ingsoft.mesos.shared.payload.server.*;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
/**
 * Dedicated RMI remote session for a single authenticated client.
 * <p>
 * Instances of this class are dynamically created by the {@link RmiSessionFactory}
 * upon successful login. This class acts as a private proxy for the client on the server,
 * maintaining their session id and holding the remote reference to their personal
 * callback receiver ({@link RmiClientService}).
 * <p>
 * It is responsible for intercepting inbound gameplay commands from the client,
 * delegating the domain logic to the central {@link ServerController}, and dispatching
 * outbound server updates back to the client via asynchronous RMI callbacks.
 */
public class VirtualViewRMI extends UnicastRemoteObject implements RmiServerService, Listener, PingPongConnection {
    private final ServerController serverController;
    private final RmiClientService clientCallback; // Per mandare gli update al client
    private final String sessionId;
    private final PreMatchPayloadMapper payloadMapper;

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback) throws RemoteException {
        super();
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.sessionId = this.serverController.registerConnection(this);
        this.payloadMapper = new PreMatchPayloadMapper();
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
        try {
            if (payload == null || payload.nickname == null || payload.nickname.isBlank()) {
                throw new IllegalArgumentException("Nickname cannot be empty");
            }
            this.serverController.authenticate(this.sessionId, payload.nickname);
            this.clientCallback.handlePreMatchUpdate(this.payloadMapper.authenticated(payload.nickname));
        } catch (IllegalArgumentException | IllegalStateException e) {
            this.clientCallback.handleError(new ErrorPayload("AUTH_ERROR", e.getMessage()));
        }
    }

    @Override
    public void joinLobby(JoinLobbyPayload payload) throws RemoteException {
        try {
            this.logReceived("JOIN_LOBBY", "lobbyId=" + payload.lobbyId);
            this.serverController.joinLobby(this.sessionId, payload.lobbyId);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("JOIN_LOBBY_ERROR", e.getMessage()));
        }
    }
    @Override
    public void createLobby(CreateLobbyPayload payload) throws RemoteException{
        try {
            this.logReceived("CREATE_LOBBY", "chosenCapacity=" + payload.chosenCapacity);
            this.serverController.createLobby(this.sessionId, payload.chosenCapacity);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("CREATE_LOBBY_ERROR", e.getMessage()));
        }
    }
    @Override
    public void exitLobby(ExitLobbyPayload payload) throws RemoteException {
        try {
            this.logReceived("EXIT_LOBBY", "{}");
            Integer lobbyId = this.serverController.lobbyIdOf(this.sessionId);
            if (lobbyId == null) {
                throw new IllegalStateException("Client is not in a lobby");
            }
            this.serverController.exitLobby(this.sessionId);

        } catch (Exception e) {

            this.clientCallback.handleError(new ErrorPayload("EXIT_LOBBY_ERROR", e.getMessage()));
        }
    }
    @Override
    public void listLobbies(ListLobbiesPayload payload) throws RemoteException {
        //questa è solo una query non modifica niente (non è update) la lasciamo cosi
        try{
            this.logReceived("LIST_LOBBIES", "{}");
            List<LobbySummaryPayload> lobbies = this.serverController.listOpenLobbies().stream()
                    .map(this::toLobbySummaryPayload)
                    .toList();
            this.clientCallback.handleLobbyList(new LobbyListPayload(lobbies));
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("LIST_LOBBY_ERROR", e.getMessage()));
        }
    }
    @Override
    public void startLobby(StartLobbyPayload payload) throws RemoteException {
        try {
            this.logReceived("START_LOBBY", "{}");
            this.serverController.startLobby(this.sessionId);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("START_LOBBY_ERROR", e.getMessage()));
        }
    }

    @Override
    public void moveTotem(MoveTotemPayload payload)  throws RemoteException {
      try{
          this.logReceived("MOVE_TOTEM", "tile=" + payload.tile);
          String nickname = this.serverController.nicknameOf(this.sessionId);
          this.serverController.gameOf(this.sessionId).moveTotem(nickname, payload.tile);
      } catch (Exception e) {
          this.clientCallback.handleError(new ErrorPayload("MOVE_TOTEM_ERROR", e.getMessage()));
      }
    }

    @Override
    public void chooseCard(ChooseCardPayload payload)  throws RemoteException {
        try{
            this.logReceived("CHOOSE_CARD", "cardId=" + payload.cardId);
            String nickname = this.serverController.nicknameOf(this.sessionId);
            this.serverController.gameOf(this.sessionId).chooseCard(nickname,payload.cardId);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("CHOSE_CARD_ERROR", e.getMessage()));
        }
    }

    @Override
    public void extraDraw(ExtraDrawPayload payload)  throws RemoteException {
        try{
            this.logReceived("EXTRA_DRAW", "cardId=" + payload.cardId);
            String nickname = this.serverController.nicknameOf(this.sessionId);
            this.serverController.gameOf(this.sessionId).extraDrawn(nickname,payload.cardId);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("EXTRA_DRAW_ERROR", e.getMessage()));
        }
    }

    @Override
    public void pong(PongPayload payload) throws RemoteException {
        if (payload == null) {
            this.clientCallback.handleError(new ErrorPayload("PONG_ERROR", "Malformed pong payload"));
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
        } catch (NoSuchObjectException ignored) {}
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

    private void logReceived(String command, String payload) {
        System.out.println("[RMI] Received " + command
                + " from " + this.describeSession()
                + " payload=" + payload);
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
}
