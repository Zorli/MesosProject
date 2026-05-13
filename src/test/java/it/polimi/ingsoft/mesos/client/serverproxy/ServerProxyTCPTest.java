package it.polimi.ingsoft.mesos.client.serverproxy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerProxyTCPTest {
    private static final Gson GSON = new Gson();

    @Test
    void authenticateWritesJsonMessage() throws Exception {
        try (TcpHarness harness = new TcpHarness()) {
            harness.proxy.authenticate("ada");

            JsonObject message = harness.readClientMessage();
            JsonObject payload = message.getAsJsonObject("payload");

            assertEquals("AUTHENTICATE", message.get("type").getAsString());
            assertEquals("ada", payload.get("nickname").getAsString());
        }
    }

    @Test
    void createLobbyWritesJsonMessage() throws Exception {
        try (TcpHarness harness = new TcpHarness()) {
            harness.proxy.createLobby(4);

            JsonObject message = harness.readClientMessage();
            JsonObject payload = message.getAsJsonObject("payload");


            assertEquals("CREATE_LOBBY", message.get("type").getAsString());
            assertEquals(4, payload.get("chosenCapacity").getAsInt());
        }
    }
    @Test
    void createLobbyListWritesJsonMessage() throws Exception {
        try (TcpHarness harness = new TcpHarness()) {

            harness.proxy.listLobbies();

            JsonObject message = harness.readClientMessage();
            JsonObject payload = message.getAsJsonObject("payload");

            assertEquals("LIST_LOBBIES", message.get("type").getAsString());
        }
    }

    @Test
    void createJoinLobbyWritesJsonMessage() throws Exception {
        try (TcpHarness harness = new TcpHarness()) {

            harness.proxy.joinLobby(7);

            JsonObject message = harness.readClientMessage();
            JsonObject payload = message.getAsJsonObject("payload");


            assertEquals("JOIN_LOBBY", message.get("type").getAsString());
            assertEquals(7, payload.get("lobbyId").getAsInt());
        }
    }

    @Test
    void closedSocketReportsConnectionClosed() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        NotConnectedSocket socket = new NotConnectedSocket();

        ServerProxyTCP proxy = new ServerProxyTCP(socket, clientView);

        proxy.authenticate("ada");

        assertEquals("CONNECTION_CLOSED", clientView.lastError.code);
        assertEquals("Client is not connected", clientView.lastError.message);

        proxy.closeConnection();
    }

    @Test
    void closeClosesSocket() throws Exception {
        try (TcpHarness harness = new TcpHarness()) {
            harness.proxy.close();


            assertTrue(harness.clientSocketIsClosed());
        }
    }

    private static final class TcpHarness implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final RecordingClientView clientView;
        private final ServerProxyTCP proxy;
        private final Socket serverSideSocket;
        private final BufferedReader reader;

        private TcpHarness() throws Exception {
            this.serverSocket = new ServerSocket(0);
            this.clientView = new RecordingClientView();
            this.proxy = new ServerProxyTCP("localhost", this.serverSocket.getLocalPort(), this.clientView);
            this.serverSideSocket = this.serverSocket.accept();
            this.reader = new BufferedReader(new InputStreamReader(
                    this.serverSideSocket.getInputStream(),
                    StandardCharsets.UTF_8
            ));
        }

        private JsonObject readClientMessage() throws Exception {
            String line = this.reader.readLine();
            return GSON.fromJson(line, JsonObject.class);
        }

        private boolean clientSocketIsClosed() {
            return !this.proxy.isConnected();
        }

        @Override
        public void close() throws Exception {
            this.proxy.closeConnection();
            this.serverSideSocket.close();
            this.serverSocket.close();
        }
    }

    private static final class NotConnectedSocket extends Socket {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final BlockingInputStream inputStream = new BlockingInputStream();
        private boolean closed;

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.outputStream;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return this.inputStream;
        }

        @Override
        public synchronized void close() {
            this.closed = true;
            this.inputStream.closeInput();
        }
    }

    private static final class BlockingInputStream extends java.io.InputStream {
        private boolean closed;

        @Override
        public synchronized int read() throws java.io.IOException {
            while (!this.closed) {
                try {
                    this.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException("Interrupted", exception);
                }
            }

            throw new java.io.IOException("Socket closed");
        }

        private synchronized void closeInput() {
            this.closed = true;
            this.notifyAll();
        }
    }

    private static final class RecordingClientView implements ClientView {
        private ErrorPayload lastError;

        @Override
        public void showError(ErrorPayload payload) {
            this.lastError = payload;
        }

        @Override
        public void showLobbyUpdate(LobbyUpdatePayload payload) {
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
        }
    }
}
