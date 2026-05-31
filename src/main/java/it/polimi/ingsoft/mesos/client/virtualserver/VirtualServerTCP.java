package it.polimi.ingsoft.mesos.client.virtualserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyTCP;
import it.polimi.ingsoft.mesos.client.view.MessageRouter;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * TCP implementation of the {@link VirtualServer} role.
 * <p>
 * In the client architecture, {@link ServerProxyTCP} and {@code VirtualServerTCP} share the same TCP socket
 * but own opposite communication directions:
 * <ul>
 *     <li>{@link ServerProxyTCP} writes client-to-preLobby commands, such as authentication or lobby actions;</li>
 *     <li>{@code VirtualServerTCP} reads preLobby-to-client messages, such as errors, lobby updates, game updates,
 *     and heartbeat pings.</li>
 * </ul>
 * <p>
 * This class is started by {@link ServerProxyTCP} in a dedicated background thread. Its job is to continuously
 * read one JSON message per line from the socket, rebuild the corresponding {@link ServerMessage}, and forward
 * it to the client view through {@link MessageRouter}.
 * <p>
 * It does not contain UI logic and it does not contain game logic. The UI-specific rendering belongs to
 * {@link ClientView}; the game rules belong to the preLobby. This class is only the TCP receiver and dispatcher
 * for messages that the preLobby sends to this client.
 * <p>
 * Connection shutdown has one important rule: if the client intentionally closes the connection, the receiver
 * must stop silently; if the connection ends without a client-side close request, the receiver reports a
 * {@code CONNECTION_CLOSED} error to the view.
 */
public class VirtualServerTCP implements VirtualServer, Runnable {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket socket;
    private final ServerProxyTCP serverProxy;
    private boolean closeRequestedByClient;
    private final MessageRouter messageRouter;

    /**
     * Creates the TCP receiver for one client connection.
     * <p>
     * The socket is the same socket used by {@link ServerProxyTCP}. This object reads from the socket input
     * stream, while the proxy writes to the socket output stream. The {@code serverProxy} reference is needed
     * for two lifecycle operations: sending a PONG when the preLobby sends PING, and closing the shared connection
     * when the receiver stops.
     *
     * @param socket      connected TCP socket shared with {@link ServerProxyTCP}
     * @param clientView  client-side view contract used by {@link MessageRouter}
     * @param serverProxy matching TCP proxy used to send PONG messages and close the connection
     */
    public VirtualServerTCP(Socket socket, ClientView clientView, ServerProxyTCP serverProxy) {
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.messageRouter = new MessageRouter(clientView);
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    /**
     * Marks that the next socket closure is expected because it was requested by the client.
     * <p>
     * This method is called by {@link ServerProxyTCP#close()} before closing the socket. Without this flag, the
     * receiver would interpret the end of the input stream as an unexpected preLobby-side disconnection and would
     * show a {@code CONNECTION_CLOSED} error to the user.
     */
    public synchronized void requestCloseByClient() {
        this.closeRequestedByClient = true;
    }

    private synchronized boolean isCloseRequestedByClient() {
        return this.closeRequestedByClient;
    }

    /**
     * Runs the TCP receiving loop.
     * <p>
     * The loop blocks on the socket input stream and reads preLobby messages line by line. Each line is expected
     * to contain one serialized JSON {@link ServerMessage}. When the input stream ends, this method decides
     * whether the closure is normal or unexpected by checking {@link #requestCloseByClient()} state.
     * <p>
     * If the connection ends unexpectedly, the method routes a {@code CONNECTION_CLOSED} error to the view. If
     * the client requested the close, no user-facing error is produced.
     * <p>
     * The {@code finally} block closes the shared TCP connection through {@link ServerProxyTCP#closeConnection()}
     * so that both directions of the socket are released when the receiver stops.
     */
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8))) {
            this.readServerMessages(reader);
            if (!this.isCloseRequestedByClient()) {
                this.handleError("CONNECTION_CLOSED", "Connection closed by preLobby");
            }
        } catch (IOException exception) {
            if (!this.isCloseRequestedByClient()) {
                this.handleError("CONNECTION_CLOSED", exception.getMessage());
            }
        } finally {
            try {
                this.serverProxy.closeConnection();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Reads all available preLobby messages from the socket input stream.
     * <p>
     * TCP is a byte stream, so this client protocol uses line separation: every preLobby message must be written
     * as one JSON line. A {@code null} result from {@link BufferedReader#readLine()} means that the other side
     * closed the stream.
     *
     * @param reader text reader wrapping the socket input stream
     * @throws IOException if the socket input stream cannot be read
     */
    private void readServerMessages(BufferedReader reader) throws IOException {
        String serverMessage;

        while ((serverMessage = reader.readLine()) != null) {
            this.handleServerMessage(serverMessage);
        }
    }

    /**
     * Converts one serialized preLobby message into the correct client-side action.
     * <p>
     * Normal application messages are deserialized into a {@link ServerMessage} and passed to
     * {@link #handleMessage(ServerMessage)}. PING is handled separately because it is a connection heartbeat:
     * it must produce a PONG response through {@link ServerProxyTCP}, not a UI update.
     * <p>
     * If the JSON is malformed, the message type is unknown, or the payload cannot be converted, the receiver
     * reports {@code INVALID_SERVER_MESSAGE}. This keeps invalid network input visible to the client without
     * crashing the receiver thread.
     *
     * @param serializedMessage one JSON line received from the preLobby
     */
    private void handleServerMessage(String serializedMessage) {
        try {
            JsonObject messageObject = GSON.fromJson(serializedMessage, JsonObject.class);
            ServerMessageType type = ServerMessageType.valueOf(messageObject.get("type").getAsString());
            JsonElement payload = messageObject.get("payload");

            if (type == ServerMessageType.PING) {
                this.handlePing();
                return;
            }

            ServerMessage message = this.buildServerMessage(type, payload);
            this.handleMessage(message);
        } catch (RuntimeException exception) {
            this.handleError("INVALID_SERVER_MESSAGE", serializedMessage);
        }
    }

    /**
     * Rebuilds the payload type expected for a preLobby message type.
     * <p>
     * Gson needs the concrete payload class to deserialize the generic {@code payload} JSON element. This method
     * is the explicit mapping between protocol message types and Java payload classes for the TCP receiver.
     *
     * @param type    preLobby message type read from the JSON envelope
     * @param payload raw JSON payload contained in the envelope
     * @return preLobby message with a typed Java payload
     */
    private ServerMessage buildServerMessage(ServerMessageType type, JsonElement payload) {
        switch (type) {
            case ERROR:
                ErrorPayload errorPayload = GSON.fromJson(payload, ErrorPayload.class);
                return new ServerMessage(type, errorPayload);
            case AUTHENTICATION_RESULT:
                AuthenticationResultPayload authenticationResultPayload = GSON.fromJson(payload, AuthenticationResultPayload.class);
                return new ServerMessage(type, authenticationResultPayload);
            case LOBBY_UPDATE:
                LobbyUpdatePayload lobbyUpdatePayload = GSON.fromJson(payload, LobbyUpdatePayload.class);
                return new ServerMessage(type, lobbyUpdatePayload);
            case LOBBY_LIST:
                LobbyListPayload lobbyListPayload = GSON.fromJson(payload, LobbyListPayload.class);
                return new ServerMessage(type, lobbyListPayload);
            case GAME_UPDATE:
                GamePayload gamePayload = GSON.fromJson(payload, GamePayload.class);
                return new ServerMessage(type, gamePayload);
            default:
                throw new IllegalArgumentException("Unsupported preLobby message type: " + type);
        }
    }

    /**
     * Routes one typed preLobby message to the client view layer.
     * <p>
     * The routing decision is delegated to {@link MessageRouter}, so this class remains focused on networking
     * and protocol conversion.
     *
     * @param message typed message received from the preLobby
     */
    @Override
    public void handleMessage(ServerMessage message) {
        this.messageRouter.routeMessage(message);
    }

    /**
     * Handles the TCP heartbeat request sent by the preLobby.
     * <p>
     * PING is not shown to the user. The receiver answers by asking {@link ServerProxyTCP} to send a PONG on
     * the client-to-preLobby direction of the same connection.
     */
    @Override
    public void handlePing() {
        this.serverProxy.sendPong();
    }


    private void handleError(String code, String message) {
        ErrorPayload payload = new ErrorPayload(code, message);
        this.handleError(payload);
    }

    /**
     * Routes a networking or protocol error to the client view using the same path as preLobby-side errors.
     * <p>
     * This keeps the UI contract simple: the view receives an {@link ErrorPayload} regardless of whether the
     * error came from the preLobby or was generated locally by the client networking layer.
     *
     * @param payload error payload to show to the client
     */
    private void handleError(ErrorPayload payload) {
        ServerMessage message = new ServerMessage(ServerMessageType.ERROR, payload);
        this.messageRouter.routeMessage(message);
    }

    /**
     * Closes the shared TCP connection through the matching {@link ServerProxyTCP}.
     * <p>
     * The actual socket is owned together with the proxy, so this method delegates to the proxy connection
     * closing primitive instead of closing only the receiver input path.
     *
     * @throws IOException if the shared TCP connection cannot be closed
     */
    @Override
    public void close() throws IOException {
        this.serverProxy.closeConnection();
    }
}
