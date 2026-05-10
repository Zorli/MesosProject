package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.PreMatchUpdatePayload;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualServerTCPTest {
    private static final Gson GSON = new Gson();

    @Test
    void serverClosedConnectionNotifiesClientAndClosesProxy() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        TestSocket socket = new TestSocket(new ByteArrayInputStream(new byte[0]));
        ServerProxyTCP proxy = new ServerProxyTCP(socket, clientView);

        proxy.waitForVirtualServer();

        assertEquals("CONNECTION_CLOSED", clientView.lastError.code);
        assertEquals("Connection closed by server", clientView.lastError.message);
        assertTrue(socket.closed);
    }

    @Test
    void clientCloseDoesNotRenderConnectionClosedError() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        CloseAwareInputStream inputStream = new CloseAwareInputStream();
        TestSocket socket = new TestSocket(inputStream);
        ServerProxyTCP proxy = new ServerProxyTCP(socket, clientView);

        proxy.close();
        proxy.waitForVirtualServer();

        assertNull(clientView.lastError);
        assertTrue(socket.closed);
    }

    private static final class TestSocket extends Socket {
        private final InputStream inputStream;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private boolean closed;

        private TestSocket(InputStream inputStream) {
            this.inputStream = inputStream;
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

        @Override
        public synchronized void close() {
            this.closed = true;
            if (this.inputStream instanceof CloseAwareInputStream closeAwareInputStream) {
                closeAwareInputStream.closeInput();
            }
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }
    }

    private static final class CloseAwareInputStream extends InputStream {
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
