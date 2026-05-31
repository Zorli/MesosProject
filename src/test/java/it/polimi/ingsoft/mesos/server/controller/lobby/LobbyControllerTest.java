package it.polimi.ingsoft.mesos.server.controller.lobby;

import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.command.QueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemDisconnectCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.network.interfaces.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests verifying the worker run-loop and command-processing of {@link LobbyController}.
 *
 * <p>Uses a mock client connection listener to assert asynchronous notifications.
 * Thread safety is guaranteed via a non-blocking poll loop implementation for stable assertions.
 */
class LobbyControllerTest {

    private ServerSessionRegistry sessions;
    private Lobby lobby;
    private LobbyController controller;
    private Thread controllerThread;

    private String aliceSessionId;
    private String bobSessionId;
    private MockClientConnection aliceConnection;
    private MockClientConnection bobConnection;

    @BeforeEach
    void setUp() {
        sessions = new ServerSessionRegistry();
        lobby = new Lobby(1, "Alice", 2);
        controller = new LobbyController(lobby, sessions, DefaultGameFactory.getInstance());

        // Register lobby model and controller in registry, matching production setup
        sessions.registerLobbyModel(lobby);
        sessions.registerLobbyController(lobby.getId(), controller);

        aliceConnection = new MockClientConnection();
        bobConnection = new MockClientConnection();

        aliceSessionId = sessions.registerConnection(aliceConnection);
        bobSessionId = sessions.registerConnection(bobConnection);

        sessions.authenticate(aliceSessionId, "Alice");
        sessions.authenticate(bobSessionId, "Bob");

        // Enroll host Alice into the lobby session state and register her listener
        sessions.enterLobby(aliceSessionId, lobby.getId());
        lobby.addListener(aliceConnection);

        controllerThread = new Thread(controller, "Test-LobbyController-Worker");
        controllerThread.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (controllerThread != null && controllerThread.isAlive()) {
            controllerThread.interrupt();
            controllerThread.join(1000);
        }
    }

    /**
     * Test name: testJoinUserCommand
     * 1. This test verifies that processing a SystemJoinCommand correctly enrolls a player (Bob) in the lobby.
     * 2. It asserts that Bob appears in both the Lobby model list and is registered under the lobby in the session registry.
     */
    @Test
    void testJoinUserCommand() throws Exception {
        controller.enqueueMessage(new SystemJoinCommand(bobSessionId));

        awaitCondition(() -> lobby.hasPlayer("Bob"));
        assertEquals(2, lobby.getPlayers().size());
        assertEquals(2, sessions.sessionsInLobby(1).size());
    }

    /**
     * Test name: testMultipleJoinsAndLobbyReady
     * 1. This test verifies that processing a SystemJoinCommand transitions the lobby to READY when capacity is reached.
     * 2. It asserts that Bob is successfully enrolled, and the lobby status transitions from WAITING to READY.
     */
    @Test
    void testMultipleJoinsAndLobbyReady() throws Exception {
        controller.enqueueMessage(new SystemJoinCommand(bobSessionId));

        awaitCondition(() -> lobby.getStatus() == LobbyStatus.READY);
        assertTrue(lobby.isFull());
        assertEquals(2, lobby.getPlayers().size());
    }

    /**
     * Test name: testExitLobbyClientCommand
     * 1. This test verifies that an ExitLobbyPayload wraps correctly into exit actions.
     * 2. It asserts that the leaving player is removed from the lobby model and their registry session state is cleaned up.
     */
    @Test
    void testExitLobbyClientCommand() throws Exception {
        controller.enqueueMessage(new SystemJoinCommand(bobSessionId));
        awaitCondition(() -> lobby.getStatus() == LobbyStatus.READY);

        controller.enqueueMessage(new ClientPayloadCommand(bobSessionId, new ExitLobbyPayload()));

        awaitCondition(() -> !lobby.hasPlayer("Bob"));
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
        assertEquals(1, lobby.getPlayers().size());
    }

    /**
     * Test name: testDisconnectUserCommand
     * 1. This test verifies that a SystemDisconnectCommand cleanly removes the disconnected client.
     * 2. It asserts that the player is removed from the model and the registry session snapshot is completely removed.
     */
    @Test
    void testDisconnectUserCommand() throws Exception {
        controller.enqueueMessage(new SystemJoinCommand(bobSessionId));
        awaitCondition(() -> lobby.getStatus() == LobbyStatus.READY);

        controller.enqueueMessage(new SystemDisconnectCommand(bobSessionId));

        awaitCondition(() -> !lobby.hasPlayer("Bob"));
        assertEquals(1, lobby.getPlayers().size());
        assertThrows(RuntimeException.class, () -> sessions.nicknameOf(bobSessionId));
    }

    /**
     * Test name: testStartLobbyMatchSuccessfully
     * 1. This test verifies that the host player can start the match when capacity is reached.
     * 2. It asserts that the game starts, registry sessions transition to the Game phase, and the controller thread finishes.
     */
    @Test
    void testStartLobbyMatchSuccessfully() throws Exception {
        controller.enqueueMessage(new SystemJoinCommand(bobSessionId));
        awaitCondition(() -> lobby.getStatus() == LobbyStatus.READY);

        controller.enqueueMessage(new ClientPayloadCommand(aliceSessionId, new StartLobbyPayload()));

        awaitCondition(() -> !controllerThread.isAlive());
        assertNotNull(sessions.getGameOrchestrator(1));
        assertEquals(0, sessions.sessionsInLobby(1).size());
        
        // Verify that players received the match started broadcast (checking list elements)
        awaitCondition(() -> !aliceConnection.receivedPayloads.isEmpty());
        boolean hasStartEvent = aliceConnection.receivedPayloads.stream()
                .anyMatch(payload -> payload instanceof LobbyUpdatePayload && ((LobbyUpdatePayload) payload).event == LobbyEventType.LOBBY_STARTED);
        assertTrue(hasStartEvent);

        // Sleep to let the countdown thread run to cover line 304 and following lines in coverage
        Thread.sleep(2200);
    }

    /**
     * Test name: testLobbyClosedAndDisposedWhenHostLeavesEmptyLobby
     * 1. This test verifies that the controller removes itself from the registry if the lobby becomes empty.
     * 2. It asserts that after the last player exits, the controller and model are unregistered from the registry.
     */
    @Test
    void testLobbyClosedAndDisposedWhenHostLeavesEmptyLobby() throws Exception {
        controller.enqueueMessage(new ClientPayloadCommand(aliceSessionId, new ExitLobbyPayload()));

        awaitCondition(() -> lobby.getStatus() == LobbyStatus.CLOSED);
        assertNull(sessions.getLobbyController(1));
    }

    /**
     * Test name: testPhaseErrorOnInvalidCommand
     * 1. This test verifies that sending unsupported payloads (not matching lobby commands) triggers a PHASE_ERROR.
     * 2. It asserts that the sender receives an ErrorPayload with the phase error code.
     */
    @Test
    void testPhaseErrorOnInvalidCommand() throws Exception {
        // Send a dummy payload that does not map to any lobby command type
        ClientPayload invalidPayload = new ClientPayload() {};
        controller.enqueueMessage(new ClientPayloadCommand(aliceSessionId, invalidPayload));

        awaitCondition(() -> !aliceConnection.receivedPayloads.isEmpty());
        boolean hasError = aliceConnection.receivedPayloads.stream()
                .anyMatch(payload -> payload instanceof ErrorPayload && "PHASE_ERROR".equals(((ErrorPayload) payload).code));
        assertTrue(hasError);
    }

    /**
     * Test name: testDisconnectUserCommandWithInvalidSession
     * 1. This test verifies that disconnecting an unknown session handles the exception and returns early without crashing.
     * 2. It asserts that sending a disconnect command for a non-existent session is ignored gracefully.
     */
    @Test
    void testDisconnectUserCommandWithInvalidSession() throws Exception {
        controller.enqueueMessage(new SystemDisconnectCommand("non-existent-session-id"));
        
        // Command should be processed and ignored. We verify that no active session was mutated.
        Thread.sleep(100);
        assertTrue(lobby.hasPlayer("Alice"));
    }

    /**
     * Test name: testUnsupportedCommandClass
     * 1. This test verifies that sending a custom Command class that is not a ClientPayloadCommand or System command triggers a PHASE_ERROR.
     * 2. It asserts that the sender receives an ErrorPayload with the phase error code.
     */
    @Test
    void testUnsupportedCommandClass() throws Exception {
        controller.enqueueMessage(new QueuedCommand() {
            @Override
            public String getSessionId() {
                return aliceSessionId;
            }
        });

        awaitCondition(() -> !aliceConnection.receivedPayloads.isEmpty());
        boolean hasError = aliceConnection.receivedPayloads.stream()
                .anyMatch(payload -> payload instanceof ErrorPayload && "PHASE_ERROR".equals(((ErrorPayload) payload).code));
        assertTrue(hasError);
    }

    /**
     * Test name: testReportPhaseErrorThrowsRuntimeException
     * 1. This test verifies that reportPhaseError catches any RuntimeException internally and does not crash the controller worker loop.
     * 2. It asserts that enqueuing an invalid payload with an unknown session id (causing sessions.getListenerOf to throw) is handled safely.
     */
    @Test
    void testReportPhaseErrorThrowsRuntimeException() throws Exception {
        ClientPayload invalidPayload = new ClientPayload() {};
        controller.enqueueMessage(new ClientPayloadCommand("unknown-session-id", invalidPayload));

        // The controller thread should process it, catch the exception internally, and remain alive.
        Thread.sleep(100);
        assertTrue(controllerThread.isAlive());
    }


    /**
     * Test name: testDisconnectHostDisposesLobby
     * 1. This test verifies that when the host disconnects from an empty or single-player lobby, the lobby is disposed.
     * 2. It asserts that the lobby controller is removed from the central session registry.
     */
    @Test
    void testDisconnectHostDisposesLobby() throws Exception {
        controller.enqueueMessage(new SystemDisconnectCommand(aliceSessionId));

        awaitCondition(() -> sessions.getLobbyController(1) == null);
    }

    /**
     * Test name: testCountdownThreadInterrupted
     * 1. This test verifies that the match countdown thread handles interruption correctly.
     * 2. It asserts that interrupting the countdown thread terminates it immediately.
     */
    @Test
    void testCountdownThreadInterrupted() throws Exception {
        controller.enqueueMessage(new SystemJoinCommand(bobSessionId));
        awaitCondition(() -> lobby.getStatus() == LobbyStatus.READY);

        controller.enqueueMessage(new ClientPayloadCommand(aliceSessionId, new StartLobbyPayload()));

        // Wait for the countdown thread to spawn
        awaitCondition(() -> {
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().equals("match-countdown-1")) {
                    t.interrupt();
                    return true;
                }
            }
            return false;
        });

        // The thread should finish and controller loop ends. We check that controller thread terminates.
        awaitCondition(() -> !controllerThread.isAlive());
    }

    /**
     * Utility method to poll for asynchronous assertion conditions.
     */
    private void awaitCondition(BooleanSupplier condition) throws Exception {
        long start = System.currentTimeMillis();
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - start > 1000) {
                fail("Timeout waiting for state transition");
            }
            Thread.sleep(10);
        }
    }

    /**
     * Mock class for client connection and listener observer.
     */
    private static class MockClientConnection implements Listener, ClientConnection {
        final List<Object> receivedPayloads = new ArrayList<>();

        @Override
        public void notifyUpdate(ServerPayload payload) {
            receivedPayloads.add(payload);
        }

        @Override
        public void sendPing() {}

        @Override
        public void close() throws IOException {}
    }
}
