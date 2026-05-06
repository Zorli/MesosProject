package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.PreMatchPayloadMapper;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
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
    private final MessageRouter router;
    private final PreMatchPayloadMapper payloadMapper;

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback, MessageRouter router) throws RemoteException {
        super();
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.sessionId = this.serverController.registerConnection(this);
        this.router = router;
        this.payloadMapper = new PreMatchPayloadMapper();
    }

    public String getSessionId() {
        return this.sessionId;
    }
    // attenzione vorrei dare alle virtualView, esse non devono avere blocchi try catch, gli errori vengono gestiti nel model
    // infatti si può notare che il notify update è perfetto riguardo a tale implementazione. (allora andrà parallelizzato comportamento di
    // gestione degli errori come nel game anche nella lobby ( o qualunque classe lato model simboleggi le lobby)
    @Override
    public void sendPing() {
        try {
            this.clientCallback.handlePing();
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void sendMessage(ClientPayload payload) throws RemoteException {
        this.router.routeMessage(this.sessionId, payload);
    }

    @Override
    public void pong(PongPayload payload) throws RemoteException {
        if (payload == null) {
            this.clientCallback.handleMessagge(new ServerMessage(ServerMessageType.ERROR, new ErrorPayload("PONG_ERROR", "Malformed pong payload")));
            return;
        }
        this.recordPong();
    }
    // da capire come sistemare (solo sessions da ServerController)

    @Override
    public void closeSession() throws RemoteException {
       // this.cleanup();
    }

    @Override
    public void close() {
        //this.cleanup();
    }

//    private void cleanup() {
//        GameOrchestrator game = this.safeGame();
//        if (game != null) {
//            game.getGame().removeListener(this);
//        }
//        this.serverController.disconnectSession(this.sessionId);
//        try {
//            UnicastRemoteObject.unexportObject(this, true);
//        } catch (NoSuchObjectException ignored) {}
//    }
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
                this.clientCallback.handleMessagge(new ServerMessage(ServerMessageType.GAME_UPDATE, gamePayload));
                return;
            }
            if (payload instanceof ErrorPayload errorPayload) {
                this.clientCallback.handleMessagge(new ServerMessage(ServerMessageType.ERROR, errorPayload));
                return;
            }
            if (payload instanceof LobbyListPayload lobbyListPayload) {
                this.clientCallback.handleLobbyList(lobbyListPayload);
                return;
            }
            if (payload instanceof PreMatchUpdatePayload preMatchUpdatePayload) {
                if (preMatchUpdatePayload.event == PreMatchEventType.LOBBY_STARTED) {
                }
                this.clientCallback.handleMessagge(new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, preMatchUpdatePayload));
            }
        } catch (RemoteException ignored) {
        }
    }

//    private void enterCurrentMatchIfAvailable() {
//        Integer lobbyId = this.serverController.lobbyIdOf(this.sessionId);
//        if (lobbyId != null) {
//            this.enterMatchIfAvailable(lobbyId);
//        }
//    }
//
//    private void enterMatchIfAvailable(int lobbyId) {
//        try {
//            this.serverController.enterGameIfAvailable(this.sessionId, lobbyId);
//        } catch (Exception ignored) {
//        }
//    }

    public void recordPong() {
        this.serverController.recordPong(this.sessionId);
    }

//    public boolean isAuthenticated() {
//        return this.serverController.isAuthenticated(this.sessionId);
//    }

    public String describeSession() {
        String nickname = this.serverController.getSessionRegistry().nicknameOf(this.sessionId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "unauthenticated-client";
    }

//    private GameOrchestrator safeGame() {
//        try {
//            return this.serverController.gameOf(this.sessionId);
//        } catch (RuntimeException ignored) {
//            return null;
//        }
//    }
}
