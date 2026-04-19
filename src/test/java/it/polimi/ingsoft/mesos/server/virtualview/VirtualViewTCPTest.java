package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the TCP virtual-view adapter used as a match observer.
 */
class VirtualViewTCPTest {
    private static final Gson GSON = new Gson();

    @Test
    void startLobbyRegistersTcpSessionsOnTheSharedMatch() throws IOException {
        ServerController controller = new ServerController();
        RecordingSocket aliceSocket = new RecordingSocket();
        RecordingSocket bobSocket = new RecordingSocket();
        VirtualViewTCP aliceView = new VirtualViewTCP(aliceSocket, controller, silentPrintStream());
        VirtualViewTCP bobView = new VirtualViewTCP(bobSocket, controller, silentPrintStream());

        aliceView.authenticate(new AuthenticationPayload("Alice"));
        bobView.authenticate(new AuthenticationPayload("Bob"));
        PreMatchUpdatePayload createLobbyPayload = (PreMatchUpdatePayload) aliceView.createLobby(new CreateLobbyPayload(2)).payload;
        int lobbyId = Integer.parseInt(createLobbyPayload.lobbyId);
        bobView.joinLobby(new JoinLobbyPayload(lobbyId));

        aliceView.startLobby(new StartLobbyPayload());
        bobView.startLobby(new StartLobbyPayload());

        GameOrchestrator orchestrator = controller.enterMatchFromLobby(lobbyId);
        orchestrator.setupMatch();

        assertEquals(1, aliceSocket.readMessages().size());
        assertEquals(1, bobSocket.readMessages().size());
        assertEquals("GAME_UPDATE", aliceSocket.readMessages().getFirst().get("type").getAsString());
        assertEquals("GAME_UPDATE", bobSocket.readMessages().getFirst().get("type").getAsString());
        assertEquals(
                orchestrator.getCurrentPayload().id,
                aliceSocket.readMessages().getFirst().getAsJsonObject("payload").get("id").getAsInt()
        );
        assertEquals(
                orchestrator.getCurrentPayload().id,
                bobSocket.readMessages().getFirst().getAsJsonObject("payload").get("id").getAsInt()
        );
    }

    @Test
    void closeDetachesTcpObserverFromMatch() throws IOException {
        ServerController controller = new ServerController();
        RecordingSocket aliceSocket = new RecordingSocket();
        VirtualViewTCP aliceView = new VirtualViewTCP(aliceSocket, controller, silentPrintStream());

        aliceView.authenticate(new AuthenticationPayload("Alice"));
        PreMatchUpdatePayload createLobbyPayload = (PreMatchUpdatePayload) aliceView.createLobby(new CreateLobbyPayload(2)).payload;
        int lobbyId = Integer.parseInt(createLobbyPayload.lobbyId);
        User bob = controller.authenticate("Bob");
        controller.joinLobby(lobbyId, bob);

        aliceView.startLobby(new StartLobbyPayload());
        aliceView.close();

        GameOrchestrator orchestrator = controller.enterMatchFromLobby(lobbyId);
        orchestrator.setupMatch();

        assertTrue(aliceSocket.readMessages().isEmpty());
    }

    @Test
    void failedTcpDeliveryRemovesTheObserverAfterTheFirstFailure() {
        GameOrchestrator orchestrator = new GameOrchestrator(
                it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory.getInstance(),
                it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures.createPlayers(2)
        );
        FailingSocket failingSocket = new FailingSocket();
        VirtualViewTCP failingView = new VirtualViewTCP(failingSocket, new ServerController(), silentPrintStream());
        failingView.setGameOrchestrator(orchestrator);

        orchestrator.setupMatch();
        int writesAfterFirstFailure = failingSocket.writeAttempts;

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");

        assertTrue(writesAfterFirstFailure > 0);
        assertEquals(writesAfterFirstFailure, failingSocket.writeAttempts);
    }

    private static PrintStream silentPrintStream() {
        return new PrintStream(OutputStream.nullOutputStream());
    }

    /**
     * Socket stub exposing only the output stream used by the TCP virtual view.
     */
    private static final class RecordingSocket extends Socket {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        @Override
        public OutputStream getOutputStream() {
            return this.outputStream;
        }

        @Override
        public synchronized void close() {
            // No-op for tests. The observer-detach assertion relies on retaining written bytes.
        }

        private List<JsonObject> readMessages() {
            String rawOutput = this.outputStream.toString(StandardCharsets.UTF_8);
            if (rawOutput.isBlank()) {
                return List.of();
            }

            return Arrays.stream(rawOutput.strip().split("\\R"))
                    .map(line -> GSON.fromJson(line, JsonObject.class))
                    .toList();
        }
    }

    /**
     * Socket stub that simulates a broken TCP connection during outbound writes.
     */
    private static final class FailingSocket extends Socket {
        private int writeAttempts;

        @Override
        public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    FailingSocket.this.writeAttempts++;
                    throw new IOException("broken pipe");
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    FailingSocket.this.writeAttempts++;
                    throw new IOException("broken pipe");
                }
            };
        }
    }
}
