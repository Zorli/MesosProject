package it.polimi.ingsoft.mesos.server.network.tcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.server.helper.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ProtocolErrorMapper;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ServerConnectionLifecycle;
import it.polimi.ingsoft.mesos.server.network.interfaces.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.server.network.interfaces.VirtualView;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.RankingPayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * TCP implementation of the server-side virtual view for one connected client.
 * <p>
 * In the project architecture, a virtual view is the network boundary that hides
 * the transport protocol from controllers and models. This concrete class is
 * used for TCP clients: it owns the accepted client {@link Socket}, reads raw
 * JSON lines from it, sends JSON responses back to it, and exposes the same
 * server-facing contracts used by the rest of the application.
 * <p>
 * Incoming traffic follows this path:
 * the TCP stream is read line by line in {@link #run()}, each raw line is
 * delegated to {@link ClientCommandHandler}, and the resulting client payload is
 * routed through {@link MessageRouter} together with the assigned session id.
 * The virtual view does not execute pre-lobby, lobby, or game
 * rules; it only attaches the network session to the command routing layer.
 * <p>
 * Outgoing traffic follows the opposite path:
 * controllers and models notify this object through {@link Listener#notifyUpdate},
 * passing a typed {@link ServerPayload}. The virtual view converts that payload
 * into the correct {@link ServerMessageType}, wraps it in a {@link ServerMessage},
 * serializes it as JSON, and writes it to the TCP socket.
 * <p>
 * The class also participates in the connection lifecycle. It implements
 * {@link ClientConnection} so the ping-pong routine can send pings and close the
 * connection, and it implements {@link VirtualView} so the command handler can
 * report client messages and pong replies back to the server-side session logic.
 */
public class VirtualViewTCP implements VirtualView, ClientConnection, Runnable, Listener{
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket clientSocket;
    private final ServerConnectionLifecycle serverConnectionLifecycle;
    private final PrintStream out;
    private final PrintWriter writer;
    private final String sessionId;
    private final MessageRouter router;
    private final ClientCommandHandler commandHandler;
    private boolean cleanupDone;

    /**
     * Creates a TCP virtual view using the standard output stream for diagnostic
     * messages.
     *
     * @param clientSocket accepted socket connected to one TCP client
     * @param serverConnectionLifecycle manager used to register and update the
     *                         session
     * @param router router used to dispatch parsed client payloads
     */
    public VirtualViewTCP(Socket clientSocket, ServerConnectionLifecycle serverConnectionLifecycle, MessageRouter router) {
        this(clientSocket, serverConnectionLifecycle, router, System.out);
    }

    /**
     * Creates a TCP virtual view for one accepted client socket.
     * <p>
     * The constructor opens the UTF-8 writer used for outgoing messages,
     * registers this virtual view as a new client connection, stores the assigned
     * session id, and creates the command handler that will parse incoming TCP
     * messages.
     *
     * @param clientSocket accepted socket connected to one TCP client
     * @param serverConnectionLifecycle manager used for session registration,
     *                         pong recording, and disconnection
     * @param router router used to send parsed client payloads to the correct
     *               server phase
     * @param out stream used for diagnostic TCP messages
     * @throws NullPointerException if {@code clientSocket},
     *                              {@code serverConnectionLifecycle}, or {@code out} is null
     * @throws IllegalStateException if the output writer cannot be opened on the
     *                               client socket
     */
    public VirtualViewTCP(Socket clientSocket, ServerConnectionLifecycle serverConnectionLifecycle, MessageRouter router, PrintStream out) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket cannot be null");
        this.serverConnectionLifecycle = Objects.requireNonNull(serverConnectionLifecycle, "serverConnectionLifecycle cannot be null");
        this.router = router;
        this.out = Objects.requireNonNull(out, "out cannot be null");
        try {
            this.writer = new PrintWriter(
                    new OutputStreamWriter(this.clientSocket.getOutputStream(), StandardCharsets.UTF_8),
                    true
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot open TCP virtual view writer", exception);
        }
        this.sessionId = this.serverConnectionLifecycle.registerConnection(this);
        this.commandHandler = new ClientCommandHandler(this.out, this);
    }

    /**
     * Runs the TCP reading loop for this client.
     * <p>
     * The method reads one line at a time from the socket using UTF-8. Each line
     * is treated as one JSON client message and passed to
     * {@link ClientCommandHandler}. If parsing or dispatching that message fails,
     * the exception is converted into an error response and sent to the same
     * client without stopping the loop immediately.
     * <p>
     * When the stream ends, the socket fails, or the thread exits the loop, the
     * virtual view closes itself and triggers the normal server disconnection
     * flow.
     */
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.clientSocket.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String rawMessage;

            while ((rawMessage = reader.readLine()) != null) {
                try {
                    this.commandHandler.handle(rawMessage);
                } catch (RuntimeException exception) {
                    this.sendError(exception);
                }
            }
        } catch (IOException exception) {
            if (!this.cleanupDone) {
                this.out.println("[TCP] Connection closed/lost for " + this.describeSession() + ": " + exception.getMessage());
            }
        } finally {
            try {
                this.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Sends one structured server message to the TCP client.
     * <p>
     * This is the low-level outgoing operation of the TCP virtual view: all
     * higher-level notifications are first converted to {@link ServerMessage}
     * objects, then serialized here as JSON and written as one line on the socket.
     *
     * @param message server message to serialize and send
     * @throws NullPointerException if {@code message} is null
     */
    private void sendServerMessage(ServerMessage message) {//low level call
        Objects.requireNonNull(message, "message cannot be null");
        this.writer.println(GSON.toJson(message));
    }

    /**
     * Sends a protocol-level error response to the client.
     * <p>
     * Runtime exceptions raised while reading, parsing, or routing a client
     * message are mapped to an {@link ErrorPayload}. The payload is then sent as
     * a {@link ServerMessageType#ERROR} message, keeping protocol errors visible
     * to the client without exposing Java exception objects.
     *
     * @param exception exception that describes the failed client command
     * @throws NullPointerException if {@code exception} is null
     */
    private void sendError(RuntimeException exception) {
        Objects.requireNonNull(exception, "exception cannot be null");
        ErrorPayload errorPayload = ProtocolErrorMapper.toErrorPayload(exception);
        this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
    }


    /**
     * Returns a human-readable name for this client session.
     * <p>
     * The nickname is read from the session registry when authentication has
     * already completed. Before authentication, the method returns a fixed label
     * used in TCP diagnostic messages.
     *
     * @return authenticated nickname, or {@code unauthenticated-client}
     */
    public String describeSession() {
        String nickname = this.serverConnectionLifecycle.getSessions().nicknameOf(this.sessionId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "unauthenticated-client";
    }

    /**
     * Routes a parsed client message into the server command layer.
     * <p>
     * The message has already been validated and deserialized by
     * {@link ClientCommandHandler}. This method attaches the current session id
     * and forwards only the payload to {@link MessageRouter}, because routing is
     * based on the session phase and on the payload command type.
     *
     * @param clientMessage typed client message produced from the TCP JSON input
     */
    @Override
    public void handleClientMessage(ClientMessage clientMessage) {
        this.router.routeMessage(this.sessionId, clientMessage.payload);
    }

    /**
     * Sends a server-side update to the TCP client.
     * <p>
     * Models and controllers use the generic {@link ServerPayload} hierarchy,
     * while the network protocol requires a {@link ServerMessageType}. This method
     * performs that mapping for every server payload currently supported by the
     * TCP protocol and writes the resulting message to the socket.
     *
     * @param payload typed payload produced by server-side model or controller
     *                logic
     */
    @Override
    public void notifyUpdate(ServerPayload payload) {
        if (payload instanceof GamePayload gamePayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.GAME_UPDATE, gamePayload));
            return;
        }
        if (payload instanceof ErrorPayload errorPayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
            return;
        }
        if (payload instanceof AuthenticationResultPayload authenticationResultPayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.AUTHENTICATION_RESULT, authenticationResultPayload));
            return;
        }
        if (payload instanceof LobbyListPayload lobbyListPayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.LOBBY_LIST, lobbyListPayload));
            return;
        }
        if (payload instanceof LobbyUpdatePayload lobbyUpdatePayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.LOBBY_UPDATE, lobbyUpdatePayload));
            return;
        }
        if (payload instanceof RankingPayload rankingPayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.RANKING_UPDATE, rankingPayload));
        }
    }


    /**
     * Sends a keep-alive ping to the TCP client.
     * <p>
     * This method is called through the {@link ClientConnection} interface by the
     * ping-pong routine. The ping has no payload: the message type is enough for
     * the client to answer with a pong.
     */
    @Override
    public void sendPing() {
        this.sendServerMessage(new ServerMessage(ServerMessageType.PING, null));
    }

    /**
     * Records a keep-alive pong received from the TCP client.
     * <p>
     * The pong payload is produced by the command handler after it recognizes a
     * {@link it.polimi.ingsoft.mesos.shared.messages.ClientMessageType#PONG}
     * message. A non-null payload means the client respected the common TCP
     * message envelope, so the session timestamp can be refreshed through the
     * server connection lifecycle.
     *
     * @param payload pong payload parsed from the client message envelope
     */
    @Override
    public void pong(PongPayload payload) {
        if (payload == null) {
            this.sendError(new IllegalArgumentException("Malformed payload"));
            return;
        }
        this.serverConnectionLifecycle.recordPong(this.sessionId);
    }


    /**
     * Closes this client session without exposing checked I/O errors.
     * <p>
     * This method is the virtual-view level close operation used by server code
     * that only needs to end the session. The actual idempotent cleanup is
     * performed by {@link #close()}.
     */
    @Override
    public void closeSession() {
        try {
            this.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Closes the TCP virtual view and starts server-side session cleanup.
     * <p>
     * The method is idempotent: only the first call disconnects the session from
     * the server connection lifecycle and closes the socket. Later calls return immediately.
     * This is important because the socket reader, timeout routine, and external
     * server code may all try to close the same connection.
     *
     * @throws IOException if closing the underlying socket fails
     */
    @Override
    public synchronized void close() throws IOException {
        if (this.cleanupDone) {
            return;
        }
        this.cleanupDone = true;
        this.serverConnectionLifecycle.disconnectSession(this.sessionId);
        if (!this.clientSocket.isClosed()) {
            this.clientSocket.close();
        }
    }


}
