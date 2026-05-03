package it.polimi.ingsoft.mesos.client.serverproxy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerProxyTCPTest {
    private static final Gson GSON = new Gson();

    @Test
    void outboundCommandsAreSerializedAsClientMessages() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        BlockingSocket socket = new BlockingSocket();
        ServerProxyTCP proxy = new ServerProxyTCP(socket, clientView);

        proxy.authenticate("Alice");
        proxy.createLobby(2);
        proxy.sendPong();
        proxy.close();

        String[] lines = socket.sentText().strip().split("\\R");
        assertEquals("AUTHENTICATE", messageType(lines[0]));
        assertEquals("Alice", messagePayload(lines[0]).get("nickname").getAsString());
        assertEquals("CREATE_LOBBY", messageType(lines[1]));
        assertEquals(2, messagePayload(lines[1]).get("chosenCapacity").getAsInt());
        assertEquals("PONG", messageType(lines[2]));
    }

    @Test
    void sendOnClosedSocketNotifiesClientView() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        BlockingSocket socket = new BlockingSocket();
        ServerProxyTCP proxy = new ServerProxyTCP(socket, clientView);

        proxy.close();
        proxy.listLobbies();

        assertEquals("CONNECTION_CLOSED", clientView.lastError.code);
        assertEquals("Client is not connected", clientView.lastError.message);
        assertFalse(proxy.isConnected());
    }

    private static String messageType(String serializedMessage) {
        return GSON.fromJson(serializedMessage, JsonObject.class)
                .get("type")
                .getAsString();
    }

    private static JsonObject messagePayload(String serializedMessage) {
        return GSON.fromJson(serializedMessage, JsonObject.class)
                .getAsJsonObject("payload");
    }

    private static final class BlockingSocket extends Socket {
        private final BlockingInputStream inputStream = new BlockingInputStream();
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private boolean closed;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public InputStream getInputStream() {
            return this.inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.outputStream;
        }

        @Override
        public synchronized void close() {
            this.closed = true;
            this.inputStream.closeInput();
        }

        private String sentText() {
            return this.outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private boolean closed;

        @Override
        public synchronized int read() throws IOException {
            while (!this.closed) {
                try {
                    this.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", exception);
                }
            }
            throw new IOException("Socket closed");
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
        public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
        }
    }
}
