package it.polimi.ingsoft.mesos.client.serverproxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerTCP;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
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
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;

/**
 * TCP implementation of the {@link ServerProxy} contract.
 * <p>
 * This class is the client-side remote proxy used by the user interface to send commands to the server.
 * The UI talks to the {@link ServerProxy} interface, so it does not need to know whether the concrete
 * connection is implemented with TCP or RMI.
 * <p>
 * In the TCP implementation, outgoing messages are written as one JSON line on the socket. Each public
 * command method creates the correct payload, wraps it inside a {@link ClientMessage}, serializes that
 * message with Gson, and writes it through the socket writer.
 * <p>
 * The same socket is also used for incoming server messages. For that reason this class creates and
 * starts a {@link VirtualServerTCP} in a dedicated thread. {@code ServerProxyTCP} owns the outgoing
 * direction, while {@code VirtualServerTCP} owns the incoming direction.
 */
public class ServerProxyTCP implements ServerProxy {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket socket;
    private final PrintWriter writer;
    private final ClientView clientView;
    private final VirtualServerTCP virtualServer;
    private final Thread virtualServerThread;

    /**
     * Opens a TCP connection to the server and starts the receiver thread for incoming server messages.
     *
     * @param serverHost server host name or IP address
     * @param port       server TCP port
     * @param clientView view contract used to show server updates and local connection errors
     * @throws IOException if the socket cannot be opened
     */
    public ServerProxyTCP(String serverHost, int port, ClientView clientView) throws IOException {
        Objects.requireNonNull(serverHost, "serverHost cannot be null");
        this.clientView = Objects.requireNonNull(clientView, "clientView cannot be null");
        this.socket = new Socket(serverHost, port);
        this.writer = new PrintWriter(this.socket.getOutputStream(), true);
        this.virtualServer = new VirtualServerTCP(this.socket, clientView, this);
        this.virtualServerThread = new Thread(this.virtualServer, "mesos-client-server-message-receiver");
        this.virtualServerThread.start();
    }

    /**
     * Creates the proxy from an already existing socket.
     * <p>
     * This constructor is package-private because it is mainly useful for tests, where the socket can be
     * controlled directly without opening a real network connection through host and port.
     *
     * @param socket     socket used for both outgoing and incoming TCP messages
     * @param clientView view contract used to show server updates and local connection errors
     * @throws IOException if the socket output stream cannot be opened
     */
    ServerProxyTCP(Socket socket, ClientView clientView) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.clientView = Objects.requireNonNull(clientView, "clientView cannot be null");
        this.writer = new PrintWriter(this.socket.getOutputStream(), true);
        this.virtualServer = new VirtualServerTCP(this.socket, clientView, this);
        this.virtualServerThread = new Thread(this.virtualServer, "mesos-client-server-message-receiver");
        this.virtualServerThread.start();
    }

    /**
     * Waits briefly for the receiver thread to finish.
     * <p>
     * This is used by tests that need deterministic control over the background {@link VirtualServerTCP}
     * thread.
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    void waitForVirtualServer() throws InterruptedException {
        this.virtualServerThread.join(1000);
    }

    /**
     * Returns whether this proxy currently considers the TCP connection usable for outgoing messages.
     * <p>
     * The check is intentionally based on the socket state owned by this proxy.
     *
     * @return {@code true} if the socket is connected and not closed, {@code false} otherwise
     */
    @Override
    public boolean isConnected() {
        return this.socket.isConnected() && !this.socket.isClosed();
    }

    /**
     * Sends one already-built client message to the server.
     * <p>
     * This is the low-level sending primitive used by all public command methods. It validates that the
     * proxy is still connected, serializes the message to JSON, writes it on the socket, and reports a
     * local connection error to the {@link ClientView} if the message cannot be sent.
     *
     * @param message application-level message to serialize and send
     */
    private void send(ClientMessage message) {
        Objects.requireNonNull(message, "message cannot be null");
        if (!this.isConnected()) {
            this.notifyLocalError("CONNECTION_CLOSED", "Client is not connected");
            return;
        }

        String serializedMessage = GSON.toJson(message);
        this.writer.println(serializedMessage);

        if (this.writer.checkError()) {
            this.notifyLocalError("CONNECTION_CLOSED", "Cannot send message to server");
        }
    }

    /**
     * Sends a local connection error to the client view.
     * <p>
     * These errors are generated by the client networking layer itself, not received from the server.
     *
     * @param code    machine-readable error code
     * @param message human-readable error message
     */
    private void notifyLocalError(String code, String message) {
        this.clientView.showError(new ErrorPayload(code, message));
    }

    /**
     * Sends a PONG message to the server.
     * <p>
     * This method is called by {@link VirtualServerTCP} when a PING message is received from the server.
     * It is part of the TCP connection keep-alive flow, not a command entered directly by the user.
     */
    public void sendPong() {
        PongPayload payload = new PongPayload();
        ClientMessage message = new ClientMessage(ClientMessageType.PONG, payload);
        this.send(message);
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
     * @param totemColor totem color requested by the host
     */
    @Override
    public void createLobby(int chosenCapacity, TotemColor totemColor) {
        CreateLobbyPayload payload = new CreateLobbyPayload(chosenCapacity, totemColor);
        ClientMessage message = new ClientMessage(ClientMessageType.CREATE_LOBBY, payload);
        this.send(message);
    }

    /**
     * Requests to join an existing lobby.
     *
     * @param lobbyId identifier of the lobby to join
     * @param totemColor totem color requested by the joining player
     */
    @Override
    public void joinLobby(int lobbyId, TotemColor totemColor) {
        JoinLobbyPayload payload = new JoinLobbyPayload(lobbyId, totemColor);
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
    @Override
    public void skipExtraDraw() {
        SkipExtraDrawPayload payload = new SkipExtraDrawPayload();
        ClientMessage clientMessage = new ClientMessage(ClientMessageType.SKIP_EXTRA_DRAW, payload);
        this.send(clientMessage);
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

    /**
     * Closes the proxy because the client intentionally requested shutdown.
     * <p>
     * This method first marks the receiver thread as closed by the client, then closes the underlying TCP
     * connection. Marking the close as client-requested prevents {@link VirtualServerTCP} from reporting
     * the shutdown as an unexpected server disconnection.
     *
     * @throws IOException if the socket cannot be closed
     */
    @Override
    public synchronized void close() throws IOException {
        this.virtualServer.requestCloseByClient();
        this.closeConnection();
    }

    /**
     * Closes the TCP resources owned by this proxy.
     * <p>
     * This method is the low-level connection close operation. It does not mark the shutdown as
     * client-requested; callers that represent an intentional client shutdown should use {@link #close()}.
     *
     * @throws IOException if the socket cannot be closed
     */
    public synchronized void closeConnection() throws IOException {
        if (!this.socket.isClosed()) {
            this.socket.close();
        }
        this.writer.close();
    }

}
