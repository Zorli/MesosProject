package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.server.network.ConnectionContext;
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
 * maintaining their specific {@link ConnectionContext} and holding the remote reference
 * to their personal callback receiver ({@link RmiClientService}).
 * <p>
 * It is responsible for intercepting inbound gameplay commands from the client,
 * delegating the domain logic to the central {@link ServerController}, and dispatching
 * outbound server updates back to the client via asynchronous RMI callbacks.
 */
public class VirtualViewRMI extends UnicastRemoteObject implements RmiServerService, Listener {
    private final ServerController serverController;
    private final ConnectionContext connectionContext;
    private final RmiClientService clientCallback; // Per mandare gli update al client
    private GameOrchestrator gameOrchestrator;

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback) throws RemoteException {
        super();
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.connectionContext = new ConnectionContext(this.serverController);
    }
    public ConnectionContext getConnectionContext() {
        return this.connectionContext;
    }
    @Override
    public void joinLobby(JoinLobbyPayload payload) throws RemoteException {
        try {
            this.logReceived("JOIN_LOBBY", "lobbyId=" + payload.lobbyId);
            //prima lo aggiungo poi chiamo join
            this.serverController.getLobbyManager().addLobbyListener(payload.lobbyId, this);
            this.serverController.joinLobby(payload.lobbyId, this.connectionContext.getUser());
            this.connectionContext.setCurrentLobbyId(payload.lobbyId);
            //nel model (joinLobby ()) sarà creato il payload e mandato a tutti i listener con il notifier
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("JOIN_LOBBY_ERROR", e.getMessage()));
        }
    }
    @Override
    public void createLobby(CreateLobbyPayload payload) throws RemoteException{
        try {
            this.logReceived("CREATE_LOBBY", "chosenCapacity=" + payload.chosenCapacity);
            Lobby lobby = this.serverController.createLobby(this.connectionContext.getUser(), payload.chosenCapacity);
            this.connectionContext.setCurrentLobbyId(lobby.getId());
            //necessario ordine invertito in creazione lobby, virtual che si avvisa da sola (unica)
            this.serverController.getLobbyManager().addLobbyListener(lobby.getId(), this);
            PreMatchUpdatePayload update = this.buildPreMatchUpdatePayload(
                    PreMatchEventType.LOBBY_CREATED,
                    this.connectionContext.getNickname(),
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
            Integer lobbyId = this.connectionContext.getCurrentLobbyId();
            if (lobbyId == null) {
                throw new IllegalStateException("Client is not in a lobby");
            }
            //qui si chiama exit, leva l'user, manda il payload a tutti, se è l'ultimo listener
            //ne elimina la lista (lobby cancellata) altrimenti nulla->torno e mi disconnetto come listener
            this.serverController.exitLobby(lobbyId, this.connectionContext.getUser());
            this.serverController.getLobbyManager().removeLobbyListener(lobbyId, this);
            this.connectionContext.setCurrentLobbyId(null);

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
            Integer lobbyId = this.connectionContext.getCurrentLobbyId();
            this.gameOrchestrator = this.serverController.createGameFromLobby(
                    lobbyId,
                    this.connectionContext.getUser()
            );
            this.connectionContext.enterMatch(this.gameOrchestrator);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("START_LOBBY_ERROR", e.getMessage()));
        }
    }

    @Override
    public void moveTotem(MoveTotemPayload payload)  throws RemoteException {
      try{
          this.logReceived("MOVE_TOTEM", "tile=" + payload.tile);
          String nickname = this.connectionContext.getNickname();
          this.gameOrchestrator.moveTotem(nickname, payload.tile);
      } catch (Exception e) {
          this.clientCallback.handleError(new ErrorPayload("MOVE_TOTEM_ERROR", e.getMessage()));
      }
    }

    @Override
    public void chooseCard(ChooseCardPayload payload)  throws RemoteException {
        try{
            this.logReceived("CHOOSE_CARD", "cardId=" + payload.cardId);
            String nickname = this.connectionContext.getNickname();
            this.gameOrchestrator.chooseCard(nickname,payload.cardId);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("CHOSE_CARD_ERROR", e.getMessage()));
        }
    }

    @Override
    public void extraDraw(ExtraDrawPayload payload)  throws RemoteException {
        try{
            this.logReceived("EXTRA_DRAW", "cardId=" + payload.cardId);
            String nickname = this.connectionContext.getNickname();
            this.gameOrchestrator.extraDrawn(nickname,payload.cardId);
        } catch (Exception e) {
            this.clientCallback.handleError(new ErrorPayload("EXTRA_DRAW_ERROR", e.getMessage()));
        }
    }

    @Override
    public void closeSession() throws RemoteException {
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
                + " from " + this.connectionContext.getNickname()
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
        Integer lobbyId = this.connectionContext.getCurrentLobbyId();
        if (lobbyId != null) {
            this.enterMatchIfAvailable(lobbyId);
        }
    }

    private void enterMatchIfAvailable(int lobbyId) {
        try {
            this.gameOrchestrator = this.serverController.getGameOrchestrator(lobbyId);
            this.connectionContext.enterMatch(this.gameOrchestrator);
        } catch (Exception ignored) {
        }
    }
}
