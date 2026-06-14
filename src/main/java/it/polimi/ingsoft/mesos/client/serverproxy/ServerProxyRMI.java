package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerRMI;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.ListLobbiesPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.SkipExtraDrawPayload;

import java.io.IOException;
import java.rmi.RemoteException;

/**
 * RMI implementation of the {@link ServerProxy} contract.
 * <p>
 * This class is the client-side remote proxy used by the user interface to send commands to the server.
 * The UI talks to the {@link ServerProxy} interface, so it does not need to know whether the concrete
 * connection is implemented with TCP or RMI.
 * <p>
 * In the RMI implementation, outgoing user commands are sent by calling methods on the remote
 * {@link RmiServerService} stub returned by the connection service. Incoming server messages travel in the
 * opposite direction through {@link VirtualServerRMI}, which is passed to the server as the client callback.
 * <p>
 * This class owns the outbound direction and the connection state. If a remote call fails, the proxy marks
 * itself as disconnected and reports a local connection error to the client view through the callback receiver.
 */
public class ServerProxyRMI implements ServerProxy {
    private RmiServerService serverStub;
    private final VirtualServerRMI callbackReceiver;
    private boolean connected;

    /**
     * Opens an RMI connection to the server and exports the client callback used for incoming messages.
     *
     * @param connectionService remote service used to create the client session
     * @param clientView        view contract used to show server updates and local connection errors
     * @throws RemoteException if the callback cannot be exported or the server cannot create the session
     */
    public ServerProxyRMI(RmiConnectionService connectionService, ClientView clientView) throws RemoteException {
        this(connectionService, clientView, true);
    }

    /**
     * Creates the proxy with explicit control over callback export.
     * <p>
     * This constructor is package-private because tests can use it to avoid exporting a real RMI object while
     * still exercising the same client-side proxy logic.
     *
     * @param connectionService remote service used to create the client session
     * @param clientView        view contract used to show server updates and local connection errors
     * @param exportCallback    {@code true} to export the callback for real RMI usage, {@code false} for tests
     * @throws RemoteException if the callback cannot be exported or the server cannot create the session
     */
    ServerProxyRMI(RmiConnectionService connectionService, ClientView clientView, boolean exportCallback) throws RemoteException {
        this.callbackReceiver = new VirtualServerRMI(clientView, exportCallback);
        this.serverStub = connectionService.connect(this.callbackReceiver);
        this.callbackReceiver.attachServerProxy(this);
        this.connected = true;
    }

    /**
     * Returns whether this proxy currently considers the RMI session usable for outgoing messages.
     *
     * @return {@code true} if the proxy has an active server stub, {@code false} after a close or remote failure
     */
    @Override
    public boolean isConnected() {
        return this.connected;
    }

    /**
     * Sends one already-built client message to the remote server.
     * <p>
     * This is the low-level sending primitive used by all public command methods except {@link #sendPong()},
     * because PONG replies use a dedicated remote method on {@link RmiServerService}.
     *
     * @param message application-level message to send to the server
     */
    private void send(ClientMessage message) {
        if (!this.ensureConnected()) {
            return;
        }

        try {
            this.serverStub.handleClientMessage(message);
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    /**
     * Sends the nickname chosen by the user to authenticate the client on the server.
     *
     * @param nickname nickname chosen by the user
     */
    @Override
    public void authenticate(String nickname) {
        AuthenticationPayload payload = new AuthenticationPayload(nickname);
        ClientMessage message = new ClientMessage(ClientMessageType.AUTHENTICATE, payload);
        this.send(message);
    }

    /**
     * Requests the list of lobbies currently available on the server.
     */
    @Override
    public void listLobbies() {
        ListLobbiesPayload payload = new ListLobbiesPayload();
        ClientMessage message = new ClientMessage(ClientMessageType.LIST_LOBBIES, payload);
        this.send(message);
    }

    /**
     * Requests the creation of a new lobby with the selected capacity.
     *
     * @param chosenCapacity number of players expected in the new lobby
     */
    @Override
    public void createLobby(int chosenCapacity) {
        CreateLobbyPayload payload = new CreateLobbyPayload(chosenCapacity);
        ClientMessage message = new ClientMessage(ClientMessageType.CREATE_LOBBY, payload);
        this.send(message);
    }

    /**
     * Requests to join an existing lobby.
     *
     * @param lobbyId identifier of the lobby to join
     */
    @Override
    public void joinLobby(int lobbyId) {
        JoinLobbyPayload payload = new JoinLobbyPayload(lobbyId);
        ClientMessage message = new ClientMessage(ClientMessageType.JOIN_LOBBY, payload);
        this.send(message);
    }

    /**
     * Requests to leave the current lobby.
     */
    @Override
    public void exitLobby() {
        ExitLobbyPayload payload = new ExitLobbyPayload();
        ClientMessage message = new ClientMessage(ClientMessageType.EXIT_LOBBY, payload);
        this.send(message);
    }

    /**
     * Requests to start the current lobby.
     */
    @Override
    public void startLobby() {
        StartLobbyPayload payload = new StartLobbyPayload();
        ClientMessage message = new ClientMessage(ClientMessageType.START_LOBBY, payload);
        this.send(message);
    }

    /**
     * Sends the tile selected by the user for the totem movement.
     *
     * @param tile identifier of the destination tile
     */
    @Override
    public void moveTotem(String tile) {
        MoveTotemPayload payload = new MoveTotemPayload(tile);
        ClientMessage message = new ClientMessage(ClientMessageType.MOVE_TOTEM, payload);
        this.send(message);
    }

    /**
     * Sends the card selected by the user during the normal card choice phase.
     *
     * @param cardId identifier of the selected card
     */
    @Override
    public void chooseCard(int cardId) {
        ChooseCardPayload payload = new ChooseCardPayload(cardId);
        ClientMessage message = new ClientMessage(ClientMessageType.CHOOSE_CARD, payload);
        this.send(message);
    }

    /**
     * Sends the card selected by the user during the extra draw phase.
     *
     * @param cardId identifier of the selected card
     */
    @Override
    public void extraDraw(int cardId) {
        ExtraDrawPayload payload = new ExtraDrawPayload(cardId);
        ClientMessage message = new ClientMessage(ClientMessageType.EXTRA_DRAW, payload);
        this.send(message);
    }

    @Override
    public void skipExtraDraw() {
        SkipExtraDrawPayload payload = new SkipExtraDrawPayload();
        ClientMessage clientMessage = new ClientMessage(ClientMessageType.SKIP_EXTRA_DRAW, payload);
        this.send(clientMessage);
    }

    /**
     * Sends a PONG message to the server.
     * <p>
     * This method is called by {@link VirtualServerRMI} when a PING message is received from the server.
     * It is part of the RMI connection keep-alive flow, not a command entered directly by the user.
     */
    public void sendPong() {
        if (!this.ensureConnected()) {
            return;
        }

        try {
            PongPayload payload = new PongPayload();
            this.serverStub.pong(payload);
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    /**
     * Marks the proxy as disconnected after a failed remote call and reports the failure locally.
     *
     * @param e remote failure raised by the RMI server stub
     */
    private void handleDisconnection(RemoteException e) {
        this.connected = false;
        this.notifyLocalError("CONNECTION_CLOSED", e.getMessage());
    }

    /**
     * Closes the RMI session owned by this proxy.
     * <p>
     * The remote session is closed first when a server stub exists. The local callback receiver is closed in
     * the {@code finally} block so that local callback resources are released even if the remote close fails.
     *
     * @throws IOException if the remote session or the local callback receiver cannot be closed
     */
    @Override
    public void close() throws IOException {
        this.connected = false;
        try {
            if (this.serverStub != null) {
                this.serverStub.closeSession();
                this.serverStub = null;
            }
        } finally {
            this.callbackReceiver.close();
        }
    }

    /**
     * Sends a local connection error to the client view through the same callback receiver used for server messages.
     * <p>
     * These errors are generated by the client networking layer itself, not received from the server.
     *
     * @param code    machine-readable error code
     * @param message human-readable error message
     */
    private void notifyLocalError(String code, String message) {
        ErrorPayload payload = new ErrorPayload(code, message);
        ServerMessage serverMessage = new ServerMessage(ServerMessageType.ERROR, payload);
        this.callbackReceiver.handleMessage(serverMessage);
    }

    /**
     * Checks whether a command can still be sent.
     * <p>
     * If the proxy is already disconnected, this method reports a local connection error and tells the caller
     * to stop before touching the remote server stub.
     *
     * @return {@code true} if a command can be sent, {@code false} if the proxy is disconnected
     */
    private boolean ensureConnected() {
        if (this.connected) {
            return true;
        }
        this.notifyLocalError("CONNECTION_CLOSED", "Client is not connected");
        return false;
    }
}
