package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
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

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback) throws RemoteException {
        super();
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.sessionId = this.serverController.registerConnection(this);
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
    public void joinLobby(JoinLobbyPayload payload) throws RemoteException {
        try {
            this.logReceived("JOIN_LOBBY", "lobbyId=" + payload.lobbyId);
            //prima lo aggiungo poi chiamo join
            this.serverController.getLobbyManager().addLobbyListener(payload.lobbyId, this);
            this.serverController.joinLobby(this.sessionId, payload.lobbyId);
            //nel model (joinLobby ()) sarà creato il payload e mandato a tutti i listener con il notifier
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("JOIN_LOBBY_ERROR", e.getMessage()));
        }
    }
    @Override
    public void createLobby(CreateLobbyPayload payload) throws RemoteException{
        try {
            this.logReceived("CREATE_LOBBY", "chosenCapacity=" + payload.chosenCapacity);
            Lobby lobby = this.serverController.createLobby(this.sessionId, payload.chosenCapacity);
            //necessario ordine invertito in creazione lobby, virtual che si avvisa da sola (unica)
            this.serverController.getLobbyManager().addLobbyListener(lobby.getId(), this);
            PreMatchUpdatePayload update = this.buildPreMatchUpdatePayload(
                    PreMatchEventType.LOBBY_CREATED,
                    this.serverController.nicknameOf(this.sessionId),
                    "Lobby created successfully",
                    lobby
            );

            this.clientCallback.handlePreMatchUpdate(update);
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
            //qui si chiama exit, leva l'user, manda il payload a tutti, se è l'ultimo listener
            //ne elimina la lista (lobby cancellata) altrimenti nulla->torno e mi disconnetto come listener
            this.serverController.exitLobby(this.sessionId);
            this.serverController.getLobbyManager().removeLobbyListener(lobbyId, this);

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
        Integer lobbyId = this.safeLobbyId();
        if (lobbyId != null) {
            this.serverController.getLobbyManager().removeLobbyListener(lobbyId, this);
        }
        GameOrchestrator game = this.safeGame();
        if (game != null) {
            game.getGame().removeListener(this);
        }
        this.serverController.disconnectSession(this.sessionId);
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {}
    }
    //
    // METODI HELPER PER COSTRUZIONE DEI PAYLOAD//
    //
    private List<String> extractPlayers(Lobby lobby) {
        return lobby.getUsers().stream()
                .map(User::getNickname)
                .toList();
    }

    private PreMatchUpdatePayload buildPreMatchUpdatePayload(PreMatchEventType event, String nickname, String message, Lobby lobby) {
        return this.buildPreMatchUpdatePayload(event, nickname, message, lobby, null);
    }

    private PreMatchUpdatePayload buildPreMatchUpdatePayload(
            PreMatchEventType event,
            String nickname,
            String message,
            Lobby lobby,
            Integer fallbackLobbyId
    ) {
        return new PreMatchUpdatePayload(
                event,
                nickname,
                message,
                lobby != null ? lobby.getId() : fallbackLobbyId,
                lobby != null ? this.extractPlayers(lobby) : List.of(),
                lobby != null ? lobby.getHostNickname() : null,
                lobby != null ? lobby.getChosenCapacity() : null
        );
    }
    private LobbySummaryPayload toLobbySummaryPayload(Lobby lobby) {
        return new LobbySummaryPayload(
                lobby.getId(),
                lobby.getHostNickname(),
                lobby.getUsers().size(),
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

    private Integer safeLobbyId() {
        try {
            return this.serverController.lobbyIdOf(this.sessionId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private GameOrchestrator safeGame() {
        try {
            return this.serverController.gameOf(this.sessionId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
