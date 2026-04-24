package it.polimi.ingsoft.mesos.client.virtualserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TcpServerMessageReceiverTest {
    private static final Gson GSON = new GsonBuilder().create();

    @Test
    void runDispatchesPreMatchUpdateFromSocket() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
             Socket serverSideSocket = serverSocket.accept()) {
            RecordingVirtualServer virtualServer = new RecordingVirtualServer();
            TcpServerMessageReceiver receiver = new TcpServerMessageReceiver(clientSocket, virtualServer);
            Thread thread = new Thread(receiver);
            thread.start();

            PrintWriter writer = new PrintWriter(serverSideSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            writer.println(GSON.toJson(new ServerMessage(
                    ServerMessageType.PRE_MATCH_UPDATE,
                    new PreMatchUpdatePayload(
                            PreMatchEventType.LOBBY_JOINED,
                            "Alice",
                            "Lobby joined successfully",
                            4,
                            List.of("Alice", "Bob"),
                            "Bob",
                            4
                    )
            )));
            serverSideSocket.shutdownOutput();

            thread.join(1000);

            assertNotNull(virtualServer.preMatchUpdatePayload);
            assertEquals(PreMatchEventType.LOBBY_JOINED, virtualServer.preMatchUpdatePayload.event);
            assertEquals(4, virtualServer.preMatchUpdatePayload.lobbyId);
        }
    }

    @Test
    void runMapsMalformedServerMessageToProtocolError() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
             Socket serverSideSocket = serverSocket.accept()) {
            RecordingVirtualServer virtualServer = new RecordingVirtualServer();
            TcpServerMessageReceiver receiver = new TcpServerMessageReceiver(clientSocket, virtualServer);
            Thread thread = new Thread(receiver);
            thread.start();

            PrintWriter writer = new PrintWriter(serverSideSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            writer.println("not-json");
            serverSideSocket.shutdownOutput();

            thread.join(1000);

            assertNotNull(virtualServer.errorPayload);
            assertEquals("INVALID_SERVER_MESSAGE", virtualServer.errorPayload.code);
            assertEquals("not-json", virtualServer.errorPayload.message);
        }
    }

    private static final class RecordingVirtualServer implements VirtualServer {
        private it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload errorPayload;
        private PreMatchUpdatePayload preMatchUpdatePayload;
        private LobbyListPayload lobbyListPayload;
        private it.polimi.ingsoft.mesos.shared.payload.server.GamePayload gamePayload;

        @Override
        public void handleError(it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload payload) {
            this.errorPayload = payload;
        }

        @Override
        public void handlePreMatchUpdate(PreMatchUpdatePayload payload) {
            this.preMatchUpdatePayload = payload;
        }

        @Override
        public void handleLobbyList(LobbyListPayload payload) {
            this.lobbyListPayload = payload;
        }

        @Override
        public void handleGameUpdate(it.polimi.ingsoft.mesos.shared.payload.server.GamePayload payload) {
            this.gamePayload = payload;
        }

        @Override
        public void close() throws IOException {
        }
    }
}
