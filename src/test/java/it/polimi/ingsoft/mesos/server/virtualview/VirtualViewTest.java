package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualViewTest {
    private static final Gson GSON = new Gson();
    private static final GameFactory UNUSED_GAME_FACTORY = new GameFactory() {
        @Override
        public Game createGame(int gameId, List<Player> players) {
            throw new UnsupportedOperationException("Game creation is not used by virtual view tests");
        }
    };

    @Test
    void tcpRunRoutesClientMessagesRecordsPongAndClosesSession() {
        Harness harness = new Harness();
        String clientMessages = GSON.toJson(new ClientMessage(
                ClientMessageType.AUTHENTICATE,
                new AuthenticationPayload("ada")
        )) + "\n" + GSON.toJson(new ClientMessage(
                ClientMessageType.PONG,
                new PongPayload()
        )) + "\n";
        ScriptedSocket socket = new ScriptedSocket(clientMessages);
        VirtualViewTCP view = new VirtualViewTCP(socket, harness.controller, harness.router, harness.log);
        String sessionId = harness.controller.lastRegisteredSessionId;

        view.run();

        AuthenticationPayload routedPayload = assertInstanceOf(
                AuthenticationPayload.class,
                harness.router.lastPayload()
        );
        assertEquals("ada", routedPayload.nickname);
        assertEquals(sessionId, harness.router.lastSessionId());
        assertEquals(List.of(sessionId), harness.controller.pongSessionIds);
        assertEquals(List.of(sessionId), harness.controller.disconnectedSessionIds);
        assertTrue(socket.isClosed());
    }

    @Test
    void tcpNotifyUpdateSerializesSupportedServerPayloadsAndPing() throws Exception {
        Harness harness = new Harness();
        ScriptedSocket socket = new ScriptedSocket("");
        VirtualViewTCP view = new VirtualViewTCP(socket, harness.controller, harness.router, harness.log);

        view.notifyUpdate(gamePayload());
        view.notifyUpdate(new ErrorPayload("SERVER_ERROR", "no"));
        view.notifyUpdate(lobbyListPayload());
        view.notifyUpdate(lobbyStartedPayload());
        view.sendPing();
        view.close();

        List<JsonObject> messages = socket.outputMessages();
        assertEquals(List.of("GAME_UPDATE", "ERROR", "LOBBY_LIST", "LOBBY_UPDATE", "PING"),
                messages.stream().map(message -> message.get("type").getAsString()).toList());
        assertEquals("SERVER_ERROR", messages.get(1).getAsJsonObject("payload").get("code").getAsString());
        assertEquals("LOBBY_STARTED", messages.get(3).getAsJsonObject("payload").get("event").getAsString());
    }

    @Test
    void tcpMalformedMessageWritesProtocolError() {
        Harness harness = new Harness();
        ScriptedSocket socket = new ScriptedSocket("{}\n");
        VirtualViewTCP view = new VirtualViewTCP(socket, harness.controller, harness.router, harness.log);

        view.run();

        JsonObject errorMessage = socket.outputMessages().getFirst();
        JsonObject payload = errorMessage.getAsJsonObject("payload");
        assertEquals("ERROR", errorMessage.get("type").getAsString());
        assertEquals("INVALID_MESSAGE", payload.get("code").getAsString());
        assertEquals("Missing client message type", payload.get("message").getAsString());
    }

    @Test
    void tcpNullPongWritesProtocolErrorWithoutRecordingPong() throws Exception {
        Harness harness = new Harness();
        ScriptedSocket socket = new ScriptedSocket("");
        VirtualViewTCP view = new VirtualViewTCP(socket, harness.controller, harness.router, harness.log);

        view.pong(null);
        view.close();

        JsonObject payload = socket.outputMessages().getFirst().getAsJsonObject("payload");
        assertEquals("INVALID_MESSAGE", payload.get("code").getAsString());
        assertTrue(harness.controller.pongSessionIds.isEmpty());
    }

    @Test
    void tcpInputFailureLogsAuthenticatedNicknameAndClosesSession() {
        Harness harness = new Harness();
        FailingInputSocket socket = new FailingInputSocket();
        VirtualViewTCP view = new VirtualViewTCP(socket, harness.controller, harness.router, harness.log);
        harness.sessions.authenticate(harness.controller.lastRegisteredSessionId, "ada");

        view.run();

        assertTrue(harness.logText().contains("[TCP] Connection closed/lost for ada: read failed"));
        assertEquals(List.of(harness.controller.lastRegisteredSessionId), harness.controller.disconnectedSessionIds);
        assertTrue(socket.isClosed());
    }

    @Test
    void tcpCloseSessionIsIdempotent() {
        Harness harness = new Harness();
        ScriptedSocket socket = new ScriptedSocket("");
        VirtualViewTCP view = new VirtualViewTCP(socket, harness.controller, harness.router, harness.log);

        view.closeSession();
        view.closeSession();

        assertEquals(List.of(harness.controller.lastRegisteredSessionId), harness.controller.disconnectedSessionIds);
        assertEquals(1, socket.closeCalls);
    }

    @Test
    void tcpConstructorWrapsWriterIoFailure() {
        Harness harness = new Harness();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new VirtualViewTCP(new FailingOutputSocket(), harness.controller, harness.router, harness.log));

        assertEquals("Cannot open TCP virtual view writer", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
        assertEquals("boom", exception.getCause().getMessage());
        assertNull(harness.controller.lastRegisteredSessionId);
        assertTrue(harness.controller.disconnectedSessionIds.isEmpty());
    }

    @Test
    void rmiRejectsNullPayloadBeforeRouter() throws Exception {
        Harness harness = new Harness();
        RecordingRmiClient callback = new RecordingRmiClient();
        VirtualViewRMI view = new VirtualViewRMI(harness.controller, callback, harness.router, harness.log);

        try {
            view.sendMessage(null);

            ServerMessage errorMessage = callback.lastMessage();
            ErrorPayload errorPayload = assertInstanceOf(ErrorPayload.class, errorMessage.payload);
            assertEquals(ServerMessageType.ERROR, errorMessage.type);
            assertEquals("INVALID_MESSAGE", errorPayload.code);
            assertEquals("Malformed payload", errorPayload.message);
            assertEquals(0, harness.router.payloadCount());
        } finally {
            view.close();
        }
    }

    @Test
    void rmiSendMessageRoutesPayloadAndReportsRouterFailure() throws Exception {
        Harness harness = new Harness();
        RecordingRmiClient callback = new RecordingRmiClient();
        VirtualViewRMI view = new VirtualViewRMI(harness.controller, callback, harness.router, harness.log);

        try {
            harness.sessions.authenticate(harness.controller.lastRegisteredSessionId, "ada");
            view.sendMessage(new AuthenticationPayload("ada"));

            AuthenticationPayload routedPayload = assertInstanceOf(
                    AuthenticationPayload.class,
                    harness.router.lastPayload()
            );
            assertEquals("ada", routedPayload.nickname);
            assertEquals(harness.controller.lastRegisteredSessionId, harness.router.lastSessionId());

            harness.router.failure = new IllegalArgumentException("bad payload");
            view.sendMessage(new AuthenticationPayload("broken"));

            ServerMessage errorMessage = callback.lastMessage();
            ErrorPayload errorPayload = assertInstanceOf(ErrorPayload.class, errorMessage.payload);
            assertEquals(ServerMessageType.ERROR, errorMessage.type);
            assertEquals("INVALID_MESSAGE", errorPayload.code);
            assertEquals("bad payload", errorPayload.message);
        } finally {
            view.close();
        }
    }

    @Test
    void rmiNotifyUpdateForwardsSupportedPayloadsAndSwallowsRemoteFailures() throws Exception {
        Harness harness = new Harness();
        RecordingRmiClient callback = new RecordingRmiClient();
        VirtualViewRMI view = new VirtualViewRMI(harness.controller, callback, harness.router, harness.log);

        try {
            view.notifyUpdate(gamePayload());
            view.notifyUpdate(new ErrorPayload("SERVER_ERROR", "no"));
            view.notifyUpdate(lobbyListPayload());
            view.notifyUpdate(lobbyStartedPayload());

            assertEquals(List.of(
                    ServerMessageType.GAME_UPDATE,
                    ServerMessageType.ERROR,
                    ServerMessageType.LOBBY_LIST,
                    ServerMessageType.LOBBY_UPDATE
            ), callback.messages.stream().map(message -> message.type).toList());

            callback.messageFailure = new RemoteException("client gone");
            assertDoesNotThrow(() -> view.notifyUpdate(new ErrorPayload("IGNORED", "ignored")));
            assertEquals(4, callback.messages.size());
        } finally {
            view.close();
        }
    }

    @Test
    void rmiPingAndPongUseCallbackAndController() throws Exception {
        Harness harness = new Harness();
        RecordingRmiClient callback = new RecordingRmiClient();
        VirtualViewRMI view = new VirtualViewRMI(harness.controller, callback, harness.router, harness.log);
        String sessionId = harness.controller.lastRegisteredSessionId;

        try {
            view.sendPing();
            callback.pingFailure = new RemoteException("client gone");
            assertDoesNotThrow(view::sendPing);

            view.pong(new PongPayload());
            view.pong(null);

            ErrorPayload errorPayload = assertInstanceOf(ErrorPayload.class, callback.lastMessage().payload);
            assertEquals(1, callback.pingCalls);
            assertEquals(List.of(sessionId), harness.controller.pongSessionIds);
            assertEquals("INVALID_MESSAGE", errorPayload.code);
        } finally {
            view.close();
        }
    }

    @Test
    void rmiCloseSessionIsIdempotent() throws Exception {
        Harness harness = new Harness();
        RecordingRmiClient callback = new RecordingRmiClient();
        VirtualViewRMI view = new VirtualViewRMI(harness.controller, callback, harness.router, harness.log);

        view.closeSession();
        view.closeSession();

        assertEquals(List.of(harness.controller.lastRegisteredSessionId), harness.controller.disconnectedSessionIds);
    }

    @Test
    void rmiConnectionEntryPointCreatesDedicatedVirtualViewSession() throws Exception {
        Harness harness = new Harness();
        RecordingRmiClient callback = new RecordingRmiClient();
        RmiConnectionEntryPoint entryPoint = new RmiConnectionEntryPoint(harness.controller, harness.router);
        RmiServerService service = null;

        try {
            service = entryPoint.connect(callback);

            assertInstanceOf(VirtualViewRMI.class, service);

            service.closeSession();
            assertEquals(List.of(harness.controller.lastRegisteredSessionId), harness.controller.disconnectedSessionIds);
        } finally {
            if (service != null) {
                service.closeSession();
            }
            unexport(entryPoint);
        }
    }

    private static GamePayload gamePayload() {
        return new GamePayload(7, "RUNNING", 1, null, null, List.of(), null);
    }

    private static LobbyListPayload lobbyListPayload() {
        return new LobbyListPayload(List.of(new LobbySummaryPayload(3, "ada", 1, 4, "WAITING")));
    }

    private static LobbyUpdatePayload lobbyStartedPayload() {
        return new LobbyUpdatePayload(
                LobbyEventType.LOBBY_STARTED,
                "ada",
                "started",
                3,
                List.of("ada"),
                "ada",
                4
        );
    }

    private static void unexport(Remote remoteObject) {
        try {
            UnicastRemoteObject.unexportObject(remoteObject, true);
        } catch (NoSuchObjectException ignored) {
        }
    }

    private static final class Harness {
        private final ServerSessionRegistry sessions = new ServerSessionRegistry();
        private final RecordingServerController controller = new RecordingServerController(this.sessions);
        private final RecordingMessageRouter router = new RecordingMessageRouter(this.sessions, this.controller);
        private final ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
        private final PrintStream log = new PrintStream(this.logOutput, true, StandardCharsets.UTF_8);

        private String logText() {
            return this.logOutput.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingServerController extends ServerController {
        private String lastRegisteredSessionId;
        private final List<String> pongSessionIds = new ArrayList<>();
        private final List<String> disconnectedSessionIds = new ArrayList<>();

        private RecordingServerController(ServerSessionRegistry sessions) {
            super(UNUSED_GAME_FACTORY, sessions);
        }

        @Override
        public String registerConnection(ClientConnection connection) {
            this.lastRegisteredSessionId = super.registerConnection(connection);
            return this.lastRegisteredSessionId;
        }

        @Override
        public void recordPong(String sessionId) {
            this.pongSessionIds.add(sessionId);
            super.recordPong(sessionId);
        }

        @Override
        public void disconnectSession(String sessionId) {
            this.disconnectedSessionIds.add(sessionId);
            super.disconnectSession(sessionId);
        }
    }

    private static final class RecordingMessageRouter extends MessageRouter {
        private final List<String> sessionIds = new ArrayList<>();
        private final List<ClientPayload> payloads = new ArrayList<>();
        private RuntimeException failure;

        private RecordingMessageRouter(ServerSessionRegistry registry, ServerController serverController) {
            super(registry, serverController);
        }

        @Override
        public void routeMessage(String sessionId, ClientPayload payload) {
            if (this.failure != null) {
                throw this.failure;
            }
            this.sessionIds.add(sessionId);
            this.payloads.add(payload);
        }

        private String lastSessionId() {
            return this.sessionIds.getLast();
        }

        private ClientPayload lastPayload() {
            return this.payloads.getLast();
        }

        private int payloadCount() {
            return this.payloads.size();
        }
    }

    private static final class RecordingRmiClient implements RmiClientService {
        private final List<ServerMessage> messages = new ArrayList<>();
        private int pingCalls;
        private RemoteException messageFailure;
        private RemoteException pingFailure;

        @Override
        public void handleMessage(ServerMessage message) throws RemoteException {
            if (this.messageFailure != null) {
                throw this.messageFailure;
            }
            this.messages.add(message);
        }

        @Override
        public void handlePing() throws RemoteException {
            if (this.pingFailure != null) {
                throw this.pingFailure;
            }
            this.pingCalls++;
        }

        private ServerMessage lastMessage() {
            return this.messages.getLast();
        }
    }

    private static class ScriptedSocket extends Socket {
        private final ByteArrayInputStream inputStream;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private boolean closed;
        private int closeCalls;

        private ScriptedSocket(String clientInput) {
            this.inputStream = new ByteArrayInputStream(clientInput.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return this.inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.outputStream;
        }

        @Override
        public synchronized void close() {
            this.closed = true;
            this.closeCalls++;
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        private List<JsonObject> outputMessages() {
            return this.outputStream.toString(StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> GSON.fromJson(line, JsonObject.class))
                    .toList();
        }
    }

    private static final class FailingOutputSocket extends Socket {
        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("boom");
        }
    }

    private static final class FailingInputSocket extends ScriptedSocket {
        private FailingInputSocket() {
            super("");
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException("read failed");
        }
    }
}
