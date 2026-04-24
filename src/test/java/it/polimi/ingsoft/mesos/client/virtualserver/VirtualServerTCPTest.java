package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualServerTCPTest {
    @Test
    void authenticateSerializesClientMessageOverTcp() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            Future<String> receivedMessage = executorService.submit(() -> {
                try (Socket acceptedSocket = serverSocket.accept();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(acceptedSocket.getInputStream(), StandardCharsets.UTF_8)
                     )) {
                    return reader.readLine();
                }
            });

            RecordingClientView clientView = new RecordingClientView();
            VirtualServerTCP virtualServerTCP = new VirtualServerTCP("127.0.0.1", serverSocket.getLocalPort(), clientView);
            virtualServerTCP.authenticate("Alice");

            String serializedMessage = receivedMessage.get();
            virtualServerTCP.close();

            assertTrue(serializedMessage.contains("\"type\":\"AUTHENTICATE\""));
            assertTrue(serializedMessage.contains("\"nickname\":\"Alice\""));
        }
    }

    private static final class RecordingClientView implements ClientView {
        @Override
        public void showError(ErrorPayload payload) {
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
