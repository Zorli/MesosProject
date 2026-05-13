package it.polimi.ingsoft.mesos.client.virtualserver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyTCP;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VirtualServerTCPTest {
    private static final Gson GSON = new Gson();

    @Test
    void errorMessageIsRoutedToClientView() throws Exception {
        try (ServerProxyHarness harness = new ServerProxyHarness()) {
            String serverMessage = """
              {"type":"ERROR","payload":{"code":"SERVER_ERROR","message":"no"}}
              """;

            RecordingClientView clientView = new RecordingClientView();
            ScriptedSocket socket = new ScriptedSocket(serverMessage);

            VirtualServerTCP virtualServer = new VirtualServerTCP(
                    socket,
                    clientView,
                    harness.proxy
            );

            virtualServer.requestCloseByClient();

            virtualServer.run();

            assertEquals("SERVER_ERROR", clientView.lastError.code);
            assertEquals("no", clientView.lastError.message);
        }
    }

    @Test
    void lobbyListMessageIsRoutedToClientView() throws Exception {
        try (ServerProxyHarness harness = new ServerProxyHarness()) {
            String serverMessage = """
                    {"type":"LOBBY_LIST","payload":{"lobbies":[{"lobbyId":7,"hostNickname":"ada","currentPlayers":1,"maxPlayers":4,"status":"WAITING"}]}}
                    """;

            RecordingClientView clientView = new RecordingClientView();
            ScriptedSocket socket = new ScriptedSocket(serverMessage);

            VirtualServerTCP virtualServer = new VirtualServerTCP(
                    socket,
                    clientView,
                    harness.proxy
            );

            virtualServer.requestCloseByClient();

            virtualServer.run();

            assertEquals(1, clientView.lastLobbyList.lobbies.size());
            assertEquals(7, clientView.lastLobbyList.lobbies.get(0).lobbyId);
            assertEquals("ada", clientView.lastLobbyList.lobbies.get(0).hostNickname);
            assertNull(clientView.lastError);
        }
    }

    @Test
    void pingSendsPongWithoutRoutingToView() throws Exception {
        try (ServerProxyHarness harness = new ServerProxyHarness()) {
            String serverMessage = """
                    {"type":"PING","payload":{}}
                    """;

            RecordingClientView clientView = new RecordingClientView();
            ScriptedSocket socket = new ScriptedSocket(serverMessage);

            VirtualServerTCP virtualServer = new VirtualServerTCP(
                    socket,
                    clientView,
                    harness.proxy
            );

            virtualServer.requestCloseByClient();

            virtualServer.run();

            JsonObject clientMessage = harness.readClientMessage();

            assertEquals("PONG", clientMessage.get("type").getAsString());
            assertNull(clientView.lastError);
            assertNull(clientView.lastLobbyList);
            assertNull(clientView.lastLobbyUpdate);
            assertNull(clientView.lastGameUpdate);
            assertNull(clientView.lastFinalGameResult);
        }
    }

    @Test
    void invalidJsonReportsInvalidServerMessage() throws Exception {
        try (ServerProxyHarness harness = new ServerProxyHarness()) {
            String serverMessage = "not-json\n";

            RecordingClientView clientView = new RecordingClientView();
            ScriptedSocket socket = new ScriptedSocket(serverMessage);

            VirtualServerTCP virtualServer = new VirtualServerTCP(
                    socket,
                    clientView,
                    harness.proxy
            );

            virtualServer.requestCloseByClient();

            virtualServer.run();

            assertEquals("INVALID_SERVER_MESSAGE", clientView.lastError.code);
            assertEquals("not-json", clientView.lastError.message);
        }
    }

    @Test
    void serverEofReportsConnectionClosed() throws Exception {
        try (ServerProxyHarness harness = new ServerProxyHarness()) {
            RecordingClientView clientView = new RecordingClientView();
            ScriptedSocket socket = new ScriptedSocket("");

            VirtualServerTCP virtualServer = new VirtualServerTCP(
                    socket,
                    clientView,
                    harness.proxy
            );

            virtualServer.run();

            assertEquals("CONNECTION_CLOSED", clientView.lastError.code);
            assertEquals("Connection closed by server", clientView.lastError.message);
        }
    }

    @Test
    void clientRequestedCloseDoesNotReportConnectionClosed() throws Exception {
        try (ServerProxyHarness harness = new ServerProxyHarness()) {
            RecordingClientView clientView = new RecordingClientView();
            ScriptedSocket socket = new ScriptedSocket("");

            VirtualServerTCP virtualServer = new VirtualServerTCP(
                    socket,
                    clientView,
                    harness.proxy
            );

            virtualServer.requestCloseByClient();

            virtualServer.run();

            assertNull(clientView.lastError);
        }
    }

    private static final class ServerProxyHarness implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Socket serverSideSocket;
        private final BufferedReader serverReader;
        private final ServerProxyTCP proxy;

        private ServerProxyHarness() throws Exception {
            this.serverSocket = new ServerSocket(0);
            this.proxy = new ServerProxyTCP(
                    "localhost",
                    this.serverSocket.getLocalPort(),
                    new RecordingClientView()
            );
            this.serverSideSocket = this.serverSocket.accept();
            this.serverReader = new BufferedReader(new InputStreamReader(
                    this.serverSideSocket.getInputStream(),
                    StandardCharsets.UTF_8
            ));
        }

        private JsonObject readClientMessage() throws Exception {
            String line = this.serverReader.readLine();
            return GSON.fromJson(line, JsonObject.class);
        }

        @Override
        public void close() throws Exception {
            this.proxy.closeConnection();
            this.serverSideSocket.close();
            this.serverSocket.close();
        }
    }

    private static final class ScriptedSocket extends Socket {
        private final ByteArrayInputStream inputStream;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        private ScriptedSocket(String serverInput) {
            byte[] bytes = serverInput.getBytes(StandardCharsets.UTF_8);
            this.inputStream = new ByteArrayInputStream(bytes);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public InputStream getInputStream() {
            return this.inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.outputStream;
        }
    }

    private static final class RecordingClientView implements ClientView {
        private ErrorPayload lastError;
        private LobbyUpdatePayload lastLobbyUpdate;
        private LobbyListPayload lastLobbyList;
        private GamePayload lastGameUpdate;
        private GamePayload lastFinalGameResult;

        @Override
        public void showError(ErrorPayload payload) {
            this.lastError = payload;
        }

        @Override
        public void showLobbyUpdate(LobbyUpdatePayload payload) {
            this.lastLobbyUpdate = payload;
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
            this.lastLobbyList = payload;
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
            this.lastGameUpdate = payload;
        }

        @Override
        public void showFinalGameResult(GamePayload payload) {
            this.lastFinalGameResult = payload;
        }
    }
}
