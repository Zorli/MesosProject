package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingDeltaPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingStatus;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingEntryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

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

    @Test
    void rankingServerMessagesAreForwardedToClientView() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        GlobalRankingPayload globalRanking = new GlobalRankingPayload(List.of(
                new GlobalRankingEntryPayload("Alice", 1200, 1, 4, 2)
        ));
        FinalMatchRankingPayload finalRanking = new FinalMatchRankingPayload(
                "game-1",
                FinalMatchRankingStatus.RECORDED,
                List.of(new FinalMatchRankingDeltaPayload("Alice", 1000, 1020, 3, 1)),
                null
        );
        MatchHistoryPayload history = new MatchHistoryPayload(List.of());
        String messages = GSON.toJson(new ServerMessage(ServerMessageType.GLOBAL_RANKING, globalRanking))
                + System.lineSeparator()
                + GSON.toJson(new ServerMessage(ServerMessageType.FINAL_MATCH_RANKING, finalRanking))
                + System.lineSeparator()
                + GSON.toJson(new ServerMessage(ServerMessageType.MATCH_HISTORY, history))
                + System.lineSeparator();
        TestSocket socket = new TestSocket(new ByteArrayInputStream(messages.getBytes()));
        ServerProxyTCP proxy = new ServerProxyTCP(socket, clientView);

        proxy.waitForVirtualServer();

        assertEquals("Alice", clientView.lastGlobalRanking.entries.getFirst().username);
        assertEquals("game-1", clientView.lastFinalMatchRanking.gameId);
        assertEquals(1, clientView.lastFinalMatchRanking.deltas.getFirst().newGlobalRank);
        assertTrue(clientView.lastMatchHistory.matches.isEmpty());
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
        private GlobalRankingPayload lastGlobalRanking;
        private FinalMatchRankingPayload lastFinalMatchRanking;
        private MatchHistoryPayload lastMatchHistory;

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

        @Override
        public void showGlobalRanking(GlobalRankingPayload payload) {
            this.lastGlobalRanking = payload;
        }

        @Override
        public void showFinalMatchRanking(FinalMatchRankingPayload payload) {
            this.lastFinalMatchRanking = payload;
        }

        @Override
        public void showMatchHistory(MatchHistoryPayload payload) {
            this.lastMatchHistory = payload;
        }
    }
}
