package it.polimi.ingsoft.mesos.server.network.rmi;

import it.polimi.ingsoft.mesos.server.helper.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ProtocolErrorMapper;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ServerConnectionLifecycle;
import it.polimi.ingsoft.mesos.server.network.interfaces.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.*;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.PrintStream;
/**
 * Dedicated RMI remote session for a single connected client.
 * <p>
 * Instances of this class are created by {@link RmiConnectionEntryPoint} when a
 * client connects, before any authentication takes place. This class acts as a
 * private proxy for the client on the server, maintaining its session id and
 * holding the remote reference to its personal callback receiver
 * ({@link RmiClientService}).
 * <p>
 * It is responsible for intercepting inbound client commands,
 * delegating routing to the central {@link MessageRouter}, and dispatching
 * outbound server updates back to the client via RMI callbacks.
 */
public class VirtualViewRMI extends UnicastRemoteObject implements RmiServerService, ClientConnection, Listener {
    private final ServerConnectionLifecycle serverConnectionLifecycle;
    private final RmiClientService clientCallback;
    private final String sessionId;
    private final MessageRouter router;
    private final PrintStream out;
    private boolean cleanupDone;

    public VirtualViewRMI(ServerConnectionLifecycle serverConnectionLifecycle, RmiClientService clientCallback, MessageRouter router) throws RemoteException {
        this(serverConnectionLifecycle, clientCallback, router, System.out);
    }

    public VirtualViewRMI(ServerConnectionLifecycle serverConnectionLifecycle, RmiClientService clientCallback, MessageRouter router, PrintStream out) throws RemoteException {
        super();
        this.serverConnectionLifecycle = serverConnectionLifecycle;
        this.clientCallback = clientCallback;
        this.sessionId = this.serverConnectionLifecycle.registerConnection(this);
        this.router = router;
        this.out = out;
    }
    // VirtualView should only handle transport/protocol errors.
    // Application errors are produced by the model and sent back through notifyUpdate.


    @Override
    public void handleClientMessage(ClientMessage clientMessage) throws RemoteException {
        try {
            if (clientMessage == null || clientMessage.payload == null) {
                this.sendError(new IllegalArgumentException("Malformed message"));
                return;
            }

            ClientPayload payload = clientMessage.payload;
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

    @Override
    public void notifyUpdate(ServerPayload payload) {
        try {
            if (payload instanceof GamePayload gamePayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.GAME_UPDATE, gamePayload));
                return;
            }
            if (payload instanceof ErrorPayload errorPayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
                return;
            }
            if (payload instanceof AuthenticationResultPayload authenticationResultPayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.AUTHENTICATION_RESULT, authenticationResultPayload));
                return;
            }
            if (payload instanceof LobbyListPayload lobbyListPayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.LOBBY_LIST, lobbyListPayload));
                return;
            }
            if (payload instanceof LobbyUpdatePayload lobbyUpdatePayload) {
                this.clientCallback.handleMessage(new ServerMessage(ServerMessageType.LOBBY_UPDATE, lobbyUpdatePayload));
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
        this.serverConnectionLifecycle.recordPong(this.sessionId);
    }

    private String describePayload(ClientPayload payload) {
        if (payload == null) {
            return "null";
        }
        return payload.getClass().getSimpleName();
    }

    private String describeSession() {
        String nickname = this.serverConnectionLifecycle.getSessions().nicknameOf(this.sessionId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "unauthenticated-client";
    }

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
        this.serverConnectionLifecycle.disconnectSession(this.sessionId);
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
        }
    }

}
