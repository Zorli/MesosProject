package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.ProtocolErrorMapper;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.*;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.io.PrintStream;
/**
 * Dedicated RMI remote session for a single authenticated client.
 * <p>
 * Instances of this class are dynamically created by the {@link RmiConnectionEntryPoint}
 * upon successful login. This class acts as a private proxy for the client on the server,
 * maintaining their session id and holding the remote reference to their personal
 * callback receiver ({@link RmiClientService}).
 * <p>
 * It is responsible for intercepting inbound gameplay commands from the client,
 * delegating the domain logic to the central {@link ServerController}, and dispatching
 * outbound server updates back to the client via asynchronous RMI callbacks.
 */
public class VirtualViewRMI extends UnicastRemoteObject implements RmiServerService, ClientConnection, Listener {
    private static final int CALLBACK_ATTEMPTS = 5;
    private static final long CALLBACK_RETRY_DELAY_MILLIS = 500;

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
        this(serverController, clientCallback, router, 0, out);
    }

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback, MessageRouter router, int rmiObjectPort, PrintStream out) throws RemoteException {
        super(rmiObjectPort);
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.sessionId = this.serverController.registerConnection(this);
        this.router = router;
        this.out = out;
        this.out.println("[RMI-DEBUG][SERVER] VirtualViewRMI exported sessionId=" + this.sessionId + ", objectPort=" + rmiObjectPort + ", callback=" + clientCallback);
    }
    // VirtualView should only handle transport/protocol errors.
    // Application errors are produced by the model and sent back through notifyUpdate.


    //Gestione del messaggio in arrivo dal client, viene inoltrato al router che decidera chi ha
    //la mansione di gestirlo
    @Override
    public void handleClientMessage(ClientMessage clientMessage) throws RemoteException {
        try {
            if (clientMessage == null || !(clientMessage.payload instanceof ClientPayload payload)) {
                this.sendError(new IllegalArgumentException("Malformed message"));
                return;
            }

            this.out.println("[RMI] Received " + this.describePayload(payload) + " from " + this.describeSession());
            this.router.routeMessage(this.sessionId, payload);
        } catch (RuntimeException exception) {
            this.sendError(exception);
        }
    }

    private void sendError(RuntimeException exception) throws RemoteException {
        this.out.println("[RMI-DEBUG][SERVER] sending ERROR to " + this.describeSession() + ": " + exception.getMessage());
        ErrorPayload errorPayload = ProtocolErrorMapper.toErrorPayload(exception);
        this.sendMessageWithRetry(new ServerMessage(ServerMessageType.ERROR, errorPayload));
        this.out.println("[RMI-DEBUG][SERVER] sent ERROR to " + this.describeSession());
    }

    //Update in uscita
    @Override
    public void notifyUpdate(Object payload) {
        try {
            this.out.println("[RMI-DEBUG][SERVER] notifyUpdate payload=" + this.describeObject(payload) + " to " + this.describeSession());
            if (payload instanceof GamePayload gamePayload) {
                this.sendMessageWithRetry(new ServerMessage(ServerMessageType.GAME_UPDATE, gamePayload));
                this.out.println("[RMI-DEBUG][SERVER] sent GAME_UPDATE to " + this.describeSession());
                return;
            }
            if (payload instanceof ErrorPayload errorPayload) {
                this.sendMessageWithRetry(new ServerMessage(ServerMessageType.ERROR, errorPayload));
                this.out.println("[RMI-DEBUG][SERVER] sent ERROR to " + this.describeSession());
                return;
            }
            if (payload instanceof AuthenticationResultPayload authenticationResultPayload) {
                this.sendMessageWithRetry(new ServerMessage(ServerMessageType.AUTHENTICATION_RESULT, authenticationResultPayload));
                this.out.println("[RMI-DEBUG][SERVER] sent AUTHENTICATION_RESULT to " + this.describeSession());
                return;
            }
            if (payload instanceof LobbyListPayload lobbyListPayload) {
                this.sendMessageWithRetry(new ServerMessage(ServerMessageType.LOBBY_LIST, lobbyListPayload));
                this.out.println("[RMI-DEBUG][SERVER] sent LOBBY_LIST to " + this.describeSession());
                return;
            }
            if (payload instanceof LobbyUpdatePayload lobbyUpdatePayload) {
                this.sendMessageWithRetry(new ServerMessage(ServerMessageType.LOBBY_UPDATE, lobbyUpdatePayload));
                this.out.println("[RMI-DEBUG][SERVER] sent LOBBY_UPDATE to " + this.describeSession());
            }
        } catch (RemoteException exception) {
            this.out.println("[RMI-DEBUG][SERVER] notifyUpdate failed for " + this.describeSession() + ": " + exception);
            exception.printStackTrace(this.out);
        }
    }

    //Ping Pong Protocol
    @Override
    public void sendPing() {
        try {
            this.out.println("[RMI-DEBUG][SERVER] sending PING to " + this.describeSession());
            this.sendPingWithRetry();
            this.serverController.recordPong(this.sessionId);
            this.out.println("[RMI-DEBUG][SERVER] PING callback succeeded for " + this.describeSession());
        } catch (RemoteException exception) {
            this.out.println("[RMI-DEBUG][SERVER] PING callback failed for " + this.describeSession() + ": " + exception);
            exception.printStackTrace(this.out);
        }
    }

    private void sendMessageWithRetry(ServerMessage message) throws RemoteException {
        RemoteException lastException = null;
        for (int attempt = 1; attempt <= CALLBACK_ATTEMPTS; attempt++) {
            try {
                this.out.println("[RMI-DEBUG][SERVER] callback message attempt " + attempt + "/" + CALLBACK_ATTEMPTS + " " + message.type + " to " + this.describeSession());
                this.clientCallback.handleMessage(message);
                return;
            } catch (RemoteException exception) {
                lastException = exception;
                this.out.println("[RMI-DEBUG][SERVER] callback message attempt failed " + attempt + "/" + CALLBACK_ATTEMPTS + " " + message.type + " to " + this.describeSession() + ": " + exception);
                this.sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    private void sendPingWithRetry() throws RemoteException {
        RemoteException lastException = null;
        for (int attempt = 1; attempt <= CALLBACK_ATTEMPTS; attempt++) {
            try {
                this.out.println("[RMI-DEBUG][SERVER] callback ping attempt " + attempt + "/" + CALLBACK_ATTEMPTS + " to " + this.describeSession());
                this.clientCallback.handlePing();
                return;
            } catch (RemoteException exception) {
                lastException = exception;
                this.out.println("[RMI-DEBUG][SERVER] callback ping attempt failed " + attempt + "/" + CALLBACK_ATTEMPTS + " to " + this.describeSession() + ": " + exception);
                this.sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    private void sleepBeforeRetry(int attempt) {
        if (attempt == CALLBACK_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(CALLBACK_RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
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

    private String describeObject(Object payload) {
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
            this.out.println("[RMI-DEBUG][SERVER] close ignored, cleanup already done for sessionId=" + this.sessionId);
            return;
        }
        this.out.println("[RMI-DEBUG][SERVER] closing sessionId=" + this.sessionId + ", user=" + this.describeSession());
        this.cleanupDone = true;
        this.serverController.disconnectSession(this.sessionId);
        try {
            UnicastRemoteObject.unexportObject(this, true);
            this.out.println("[RMI-DEBUG][SERVER] unexported sessionId=" + this.sessionId);
        } catch (NoSuchObjectException ignored) {
            this.out.println("[RMI-DEBUG][SERVER] unexport skipped, no such object for sessionId=" + this.sessionId);
        }
    }

}
