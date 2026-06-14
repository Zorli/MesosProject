package it.polimi.ingsoft.mesos.server.controller.preLobby;

import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.command.QueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.factory.GameFactory;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ServerConnectionLifecycle;
import it.polimi.ingsoft.mesos.server.network.interfaces.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.PingPongRoutine;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.*;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for PreLobbyController.
 * Validates command queue processing, lobby flow (create, join, list),
 * client reconnection handling, and connection lifecycle methods.
 */
class PreLobbyControllerTest {

    private ServerSessionRegistry registry;
    private PreLobbyController controller;
    private Thread controllerThread;
    private ServerConnectionLifecycle  connectionLifecycle;
    private MockGameFactory gameFactory;

    private static Game createMockGame(int gameId) {
        List<Player> players = List.of(new Player("Alice", 2, 0, TotemColor.RED));
        TribeDeck tribeDeck = new TribeDeck(new ArrayList<>());
        BuildingDeck buildingDeck = new BuildingDeck(new ArrayList<>());
        Board board = new Board(tribeDeck, buildingDeck, players);
        return new Game(gameId, players, board);
    }

    @BeforeEach
    void setUp() {
        registry = new ServerSessionRegistry();
        gameFactory = new MockGameFactory();
        controller = new PreLobbyController(gameFactory, registry);
        connectionLifecycle = new ServerConnectionLifecycle(registry);
        controllerThread = new Thread(controller, "PreLobbyController-TestThread");
        controllerThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Stop the background thread
        controllerThread.interrupt();
        controllerThread.join(1000);

        // Reflection stop for the PingPong scheduled executor to prevent resource leaks
        Field field = ServerConnectionLifecycle.class.getDeclaredField("pingPongRoutine");
        field.setAccessible(true);
        PingPongRoutine routine = (PingPongRoutine) field.get(connectionLifecycle);
        if (routine != null) {
            routine.stop();
        }
    }

    /**
     * Verifies that the client authentication payload is parsed and processed.
     * Checks that the user is correctly registered as authenticated in the registry.
     */
    @Test
    void testAuthenticationProcessing() throws Exception {
        MockListener connection = new MockListener();
        String sid = connectionLifecycle.registerConnection(connection);

        AuthenticationPayload payload = new AuthenticationPayload("Alice");
        controller.enqueueMessage(new ClientPayloadCommand(sid, payload));

        // Wait a short time for processing
        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(registry.isAuthenticated(sid));
        assertEquals("Alice", registry.nicknameOf(sid));
        
        // Assert client received authenticated packet
        assertFalse(connection.receivedUpdates.isEmpty());
    }

    /**
     * Verifies that list, create, and join lobbies are correctly processed.
     * Checks that the lobby controller is registered and starts running upon creation.
     */
    @Test
    void testLobbyOrchestrationFlow() throws Exception {
        // Authenticate Alice
        MockListener aliceConn = new MockListener();
        String aliceSid = connectionLifecycle.registerConnection(aliceConn);
        controller.enqueueMessage(new ClientPayloadCommand(aliceSid, new AuthenticationPayload("Alice")));

        // Authenticate Bob
        MockListener bobConn = new MockListener();
        String bobSid = connectionLifecycle.registerConnection(bobConn);
        controller.enqueueMessage(new ClientPayloadCommand(bobSid, new AuthenticationPayload("Bob")));

        TimeUnit.MILLISECONDS.sleep(100);

        // Alice creates lobby
        controller.enqueueMessage(new ClientPayloadCommand(aliceSid, new CreateLobbyPayload(3)));
        TimeUnit.MILLISECONDS.sleep(150);

        // Check registry has lobby and controller
        List<it.polimi.ingsoft.mesos.server.model.lobby.Lobby> openLobbies = registry.getOpenLobbies();
        assertEquals(1, openLobbies.size());
        int lobbyId = openLobbies.get(0).getId();

        LobbyController lobbyController = registry.getLobbyController(lobbyId);
        assertNotNull(lobbyController);

        // Bob requests to list open lobbies
        controller.enqueueMessage(new ClientPayloadCommand(bobSid, new ListLobbiesPayload()));
        TimeUnit.MILLISECONDS.sleep(100);

        // Bob joins the lobby
        controller.enqueueMessage(new ClientPayloadCommand(bobSid, new JoinLobbyPayload(lobbyId)));
        TimeUnit.MILLISECONDS.sleep(150);

        // Verify Bob is added to the lobby (this would happen after LobbyController dequeues it)
        assertTrue(openLobbies.get(0).hasPlayer("Bob"));
    }

    /**
     * Verifies handling of non-ClientPayloadCommand objects in the queue.
     * Confirms that a phase error is reported to the client session when an unexpected command type is received.
     */
    @Test
    void testInvalidCommandTypeReportsPhaseError() throws Exception {
        MockListener conn = new MockListener();
        String sid = connectionLifecycle.registerConnection(conn);

        // Send a dummy command that is not a ClientPayloadCommand
        QueuedCommand dummyCmd = new QueuedCommand() {
            @Override
            public String getSessionId() {
                return sid;
            }
        };

        controller.enqueueMessage(dummyCmd);
        TimeUnit.MILLISECONDS.sleep(100);

        assertFalse(conn.receivedUpdates.isEmpty());
        Object update = conn.receivedUpdates.get(0);
        assertTrue(update instanceof ErrorPayload);
        assertEquals("PHASE_ERROR", ((ErrorPayload) update).code);
    }

    /**
     * Verifies that IllegalArgumentExceptions in payload parsing trigger phase errors.
     * Ensures unexpected payloads cause error replies to be sent to the unicast connection.
     */
    @Test
    void testUnexpectedPayloadTriggersPhaseError() throws Exception {
        MockListener conn = new MockListener();
        String sid = connectionLifecycle.registerConnection(conn);

        ClientPayload unexpectedPayload = new ClientPayload() {};
        controller.enqueueMessage(new ClientPayloadCommand(sid, unexpectedPayload));
        TimeUnit.MILLISECONDS.sleep(100);

        assertFalse(conn.receivedUpdates.isEmpty());
        Object update = conn.receivedUpdates.get(0);
        assertTrue(update instanceof ErrorPayload);
        assertEquals("PHASE_ERROR", ((ErrorPayload) update).code);
    }

    /**
     * Verifies that joining a non-existent lobby reports a validation join error.
     * Validates error reporting pathways under invalid lobby parameters.
     */
    @Test
    void testJoinNonExistentLobbyReportsError() throws Exception {
        MockListener conn = new MockListener();
        String sid = connectionLifecycle.registerConnection(conn);
        controller.enqueueMessage(new ClientPayloadCommand(sid, new AuthenticationPayload("Charlie")));
        TimeUnit.MILLISECONDS.sleep(50);

        controller.enqueueMessage(new ClientPayloadCommand(sid, new JoinLobbyPayload(999))); // lobby 999 does not exist
        TimeUnit.MILLISECONDS.sleep(100);

        // Last message should be validation error
        Object lastMsg = conn.receivedUpdates.get(conn.receivedUpdates.size() - 1);
        assertTrue(lastMsg instanceof ErrorPayload);
        assertEquals("JOIN_ERROR", ((ErrorPayload) lastMsg).code);
    }

    /**
     * Verifies client reconnect behavior when a disconnected player logs back in.
     * Validates that reconnect triggers game controller updates and system join command.
     */
    @Test
    void testAuthenticationWithPendingReconnection() throws Exception {
        String oldSid = registry.registerConnection(new MockListener());
        registry.authenticate(oldSid, "Alice");
        registry.recordGameDisconnection(oldSid, 5);
        registry.unregister(oldSid);

        MockListener conn = new MockListener();
        String sid = connectionLifecycle.registerConnection(conn);

        // Mock game orchestrator in registry
        MockGameController mockGameCtrl = new MockGameController(5, registry);
        registry.registerGameOrchestrator(5, mockGameCtrl);

        // Alice authenticates
        controller.enqueueMessage(new ClientPayloadCommand(sid, new AuthenticationPayload("Alice")));
        TimeUnit.MILLISECONDS.sleep(150);

        // The game controller should have received the SystemJoinCommand
        assertFalse(mockGameCtrl.receivedCommands.isEmpty());
        assertTrue(mockGameCtrl.receivedCommands.get(0) instanceof SystemJoinCommand);
        assertEquals(sid, mockGameCtrl.receivedCommands.get(0).getSessionId());
    }

    /**
     * Verifies that direct connection lifecycle methods execute correctly.
     * Checks registerConnection, recordPong, disconnectSession, and reportPhaseError directly.
     */
    @Test
    void testDirectLifecycleMethods() {
        MockListener conn = new MockListener();
        
        // Register
        String sid = connectionLifecycle.registerConnection(conn);
        assertNotNull(sid);
        assertTrue(registry.snapshots().stream().anyMatch(s -> s.sessionId().equals(sid)));

        // Record Pong
        assertDoesNotThrow(() -> connectionLifecycle.recordPong(sid));

        // Report Phase Error
        controller.reportPhaseError(sid);
        assertFalse(conn.receivedUpdates.isEmpty());
        Object err = conn.receivedUpdates.get(0);
        assertTrue(err instanceof ErrorPayload);
        assertEquals("PHASE_ERROR", ((ErrorPayload) err).code);

        // Disconnect
        connectionLifecycle.disconnectSession(sid);
        assertTrue(registry.snapshots().stream().noneMatch(s -> s.sessionId().equals(sid)));
    }

    private static class MockGameFactory implements GameFactory {
        @Override
        public it.polimi.ingsoft.mesos.server.model.game.game.Game createGame(int gameId, List<it.polimi.ingsoft.mesos.server.model.game.player.Player> players) {
            return null;
        }
    }

    private static class MockListener implements ClientConnection, Listener {
        final List<Object> receivedUpdates = new ArrayList<>();

        @Override
        public void sendPing() {}

        @Override
        public void close() throws IOException {}

        @Override
        public void notifyUpdate(ServerPayload update) {
            receivedUpdates.add(update);
        }
    }

    private static class MockGameController extends GameController {
        final List<QueuedCommand> receivedCommands = new ArrayList<>();

        public MockGameController(int gameId, ServerSessionRegistry registry) {
            super(createMockGame(gameId), "Alice", registry);
        }

        @Override
        public void enqueueMessage(QueuedCommand command) {
            receivedCommands.add(command);
        }
    }
}
