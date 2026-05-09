package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.ProtocolErrorMapper;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.*;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.PrintStream;
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
public class VirtualViewRMI extends UnicastRemoteObject implements RmiServerService, ClientConnection, Listener {
    private final ServerController serverController;
    private final RmiClientService clientCallback; // Per mandare gli update al client
    private final String sessionId;
    private final MessageRouter router;
    private final PrintStream out;
    private boolean cleanupDone;

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback, MessageRouter router) throws RemoteException {
        this(serverController, clientCallback, router, System.out);
    }

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback, MessageRouter router, PrintStream out) throws RemoteException {
        super();
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.sessionId = this.serverController.registerConnection(this);
        this.router = router;
        this.out = out;
    }
    // VirtualView should only handle transport/protocol errors.
    // Application errors are produced by the model and sent back through notifyUpdate.


    //Gestione del messaggio in arrivo dal client, viene inoltrato al router che decidera chi ha
    //la mansione di gestirlo
    @Override
    public void sendMessage(ClientPayload payload) throws RemoteException {
        try {
            this.out.println("[RMI] Received " + this.describePayload(payload) + " from " + this.describeSession());
            this.router.routeMessage(this.sessionId, payload);
        } catch (RuntimeException exception) {
            this.sendError(exception);
        }
    }

    private void sendError(RuntimeException exception) throws RemoteException {
        ErrorPayload errorPayload = ProtocolErrorMapper.toErrorPayload(exception);
        this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
    }

    //Update in uscita
    @Override
    public void notifyUpdate(Object payload) {
        try {
            if (payload instanceof GamePayload gamePayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.GAME_UPDATE, gamePayload));
                return;
            }
            if (payload instanceof ErrorPayload errorPayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
                return;
            }
            if (payload instanceof LobbyListPayload lobbyListPayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.LOBBY_LIST, lobbyListPayload));
                return;
            }
            if (payload instanceof PreMatchUpdatePayload preMatchUpdatePayload) {
                if (preMatchUpdatePayload.event == PreMatchEventType.LOBBY_STARTED) {
                }
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, preMatchUpdatePayload));
            }
        } catch (RemoteException ignored) {
        }
    }

    //Ping Pong Protocol
    @Override
    public void sendPing() {
        try {
            this.clientCallback.handlePing();
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void pong(PongPayload payload) throws RemoteException {
        this.out.println("[RMI] Received PONG from " + this.describeSession());
        if (payload == null) {
            this.sendError(new IllegalArgumentException("Malformed payload"));
            return;
        }
        this.serverController.recordPong(this.sessionId);
    }

    private String describePayload(ClientPayload payload) {
        if (payload == null) {
            return "null";
        }
        return payload.getClass().getSimpleName();
    }

    private String describeSession() {
        String nickname = this.serverController.getSessionRegistry().nicknameOf(this.sessionId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "unauthenticated-client";
    }

    //metodi di chiusura pulita

    @Override
    public void closeSession() throws RemoteException {
        this.close();
    }

    @Override
    public synchronized void close() {
        if (this.cleanupDone) {
            return;
        }
        this.cleanupDone = true;
        this.serverController.disconnectSession(this.sessionId);
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
        }
    }

}
