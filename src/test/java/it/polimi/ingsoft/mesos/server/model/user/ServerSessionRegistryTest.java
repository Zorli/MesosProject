package it.polimi.ingsoft.mesos.server.model.user;

import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry.Phase;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry.SessionSnapshot;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.server.User;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ServerSessionRegistry covering lobby lifecycle management,
 * controller registration, disconnection/reconnection logic, session snapshots, and getters.
 */
class ServerSessionRegistryTest {

    private static Game createMockGame(int gameId) {
        List<Player> players = List.of(new Player("Alice", 2, 0, TotemColor.RED));
        TribeDeck tribeDeck = new TribeDeck(new ArrayList<>());
        BuildingDeck buildingDeck = new BuildingDeck(new ArrayList<>());
        Board board = new Board(tribeDeck, buildingDeck, players);
        return new Game(gameId, players, board);
    }

    /**
     * Verifies that the registry can create and register a Lobby model.
     * Checks that the model is stored under its generated ID and can be queried.
     */
    @Test
    void testLobbyModelLifecycle() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        Lobby lobby = registry.createLobbyModel("Alice", 4);

        assertNotNull(lobby);
        assertEquals(1, lobby.getId());
        assertEquals("Alice", lobby.getHostNickname());
        assertEquals(4, lobby.getChosenCapacity());

        Optional<Lobby> fetched = registry.getLobbyModel(1);
        assertTrue(fetched.isPresent());
        assertEquals(lobby, fetched.get());

        Lobby required = registry.requireLobbyModel(1);
        assertEquals(lobby, required);

        registry.removeLobbyModel(1);
        assertFalse(registry.getLobbyModel(1).isPresent());
        assertThrows(IllegalArgumentException.class, () -> registry.requireLobbyModel(1));
    }

    /**
     * Verifies that duplicate lobby models cannot be registered, and checks updating the ID sequence.
     */
    @Test
    void testDuplicateLobbyRegistration() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        Lobby lobby = new Lobby(10, "Host", 3);
        registry.registerLobbyModel(lobby);

        assertThrows(IllegalArgumentException.class, () -> registry.registerLobbyModel(lobby));
    }

    /**
     * Verifies query methods for open lobbies and players currently inside any lobby.
     * Checks list filter exclusions on CLOSED or STARTED statuses.
     */
    @Test
    void testOpenLobbiesAndPlayerPresenceQueries() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        Lobby lobby1 = registry.createLobbyModel("Alice", 3);
        Lobby lobby2 = registry.createLobbyModel("Bob", 4);

        // Host is automatically in lobby1
        assertTrue(registry.isPlayerInAnyLobby("Alice"));
        assertFalse(registry.isPlayerInAnyLobby("Charlie"));

        List<Lobby> openLobbies = registry.getOpenLobbies();
        assertEquals(2, openLobbies.size());

        // Fill lobby1 to capacity to start it
        lobby1.addPlayer("Charlie", new DummyListener());
        lobby1.addPlayer("Dave", new DummyListener());
        assertTrue(lobby1.startMatch("Alice"));

        openLobbies = registry.getOpenLobbies();
        assertEquals(1, openLobbies.size());
        assertEquals(lobby2, openLobbies.get(0));

        // Player Alice is in a started lobby, so not in an "open/unstarted" lobby query anymore
        assertFalse(registry.isPlayerInAnyLobby("Alice"));
    }

    /**
     * Verifies active controller mapping registration and removal.
     * Validates both LobbyController and GameController registries.
     */
    @Test
    void testControllerRegistry() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        
        // Setup dummy controllers (mocking or subclassing is not needed, can test null checks and lookup)
        assertNull(registry.getLobbyController(9));
        assertNull(registry.getGameOrchestrator(9));

        LobbyController dummyLobbyCtrl = new LobbyController(new Lobby(1, "H", 2), registry, (gameId, players) -> null);
        registry.registerLobbyController(1, dummyLobbyCtrl);
        assertEquals(dummyLobbyCtrl, registry.getLobbyController(1));

        registry.removeLobbyController(1);
        assertNull(registry.getLobbyController(1));

        GameController dummyGameCtrl = new GameController(createMockGame(1), "Alice", registry);
        registry.registerGameOrchestrator(1, dummyGameCtrl);
        assertEquals(dummyGameCtrl, registry.getGameOrchestrator(1));

        registry.removeGameOrchestrator(1);
        assertNull(registry.getGameOrchestrator(1));
    }

    /**
     * Verifies basic authentication lifecycle of a session connection.
     * Confirms that valid/invalid nicknames throw correctly and return isAuth correctly.
     */
    @Test
    void testSessionAuthentication() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        DummyConnection conn = new DummyConnection();

        String sid = registry.registerConnection(conn);
        assertFalse(registry.isAuthenticated(sid));

        User user = registry.authenticate(sid, "Alice");
        assertNotNull(user);
        assertEquals("Alice", user.getNickname());
        assertTrue(registry.isAuthenticated(sid));

        // Authenticated session cannot authenticate again
        assertThrows(IllegalStateException.class, () -> registry.authenticate(sid, "Bob"));

        // Blank/Null nickname check
        String sid2 = registry.registerConnection(conn);
        assertThrows(IllegalArgumentException.class, () -> registry.authenticate(sid2, ""));
        assertThrows(IllegalArgumentException.class, () -> registry.authenticate(sid2, "  "));

        // Duplicate nickname check
        assertThrows(IllegalArgumentException.class, () -> registry.authenticate(sid2, "Alice"));
    }

    /**
     * Verifies game state phase transitions on a session.
     * Ensures moving from pre-lobby, to lobby, to game, and back resets phase properties.
     */
    @Test
    void testSessionPhaseTransitions() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        DummyConnection conn = new DummyConnection();
        String sid = registry.registerConnection(conn);

        assertEquals(Phase.PRE_LOBBY, registry.getPhaseOf(sid));

        registry.enterLobby(sid, 100);
        assertEquals(Phase.LOBBY, registry.getPhaseOf(sid));
        assertEquals(100, registry.lobbyIdOf(sid));
        assertNull(registry.gameIdOf(sid));

        registry.leaveLobby(sid);
        assertEquals(Phase.PRE_LOBBY, registry.getPhaseOf(sid));
        assertNull(registry.lobbyIdOf(sid));

        registry.enterGame(sid, 200);
        assertEquals(Phase.GAME, registry.getPhaseOf(sid));
        assertEquals(200, registry.gameIdOf(sid));
        assertNull(registry.lobbyIdOf(sid));

        registry.leaveGame(sid);
        assertEquals(Phase.PRE_LOBBY, registry.getPhaseOf(sid));
        assertNull(registry.gameIdOf(sid));
    }

    /**
     * Verifies session disconnection and unregistration cleanup.
     * Confirms that unregistering correctly releases nickname locks.
     */
    @Test
    void testSessionCleanupAndUnregister() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        DummyConnection conn = new DummyConnection();
        String sid = registry.registerConnection(conn);

        registry.authenticate(sid, "Alice");
        assertTrue(registry.isAuthenticated(sid));

        registry.unregister(sid);
        assertFalse(registry.isAuthenticated(sid));

        // Now nickname Alice should be free again
        String sid2 = registry.registerConnection(conn);
        assertDoesNotThrow(() -> registry.authenticate(sid2, "Alice"));
    }

    /**
     * Verifies game disconnection and re-connection logic.
     * Validates maps keeping track of players re-entering matches.
     */
    @Test
    void testGameDisconnectionAndReconnectionFlow() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        DummyListener conn = new DummyListener();
        String sid = registry.registerConnection(conn);
        registry.authenticate(sid, "Alice");

        assertFalse(registry.isDisconnected("Alice"));

        // Register disconnection
        registry.RegisterDisconnection(sid, 5);
        registry.unregister(sid); // Clear nickname lock on old session
        assertTrue(registry.isDisconnected("Alice"));
        assertEquals(5, registry.DisconnectedFrom("Alice"));

        // Reconnect session
        String newSid = registry.registerConnection(conn);
        registry.authenticate(newSid, "Alice");
        assertTrue(registry.isReconnectedInGame("Alice", 5, sid));

        registry.RegisterReConnection(newSid);
        assertFalse(registry.isDisconnected("Alice"));
        assertEquals(Phase.GAME, registry.getPhaseOf(newSid));
        assertEquals(5, registry.gameIdOf(newSid));
    }

    /**
     * Verifies clearing disconnected entries by game ID or nickname.
     */
    @Test
    void testRemoveDisconnectedReferences() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        DummyConnection conn = new DummyConnection();
        String sid = registry.registerConnection(conn);
        registry.authenticate(sid, "Alice");

        registry.RegisterDisconnection(sid, 10);
        assertTrue(registry.isDisconnected("Alice"));

        registry.removeDisconnectedReference("Alice");
        assertFalse(registry.isDisconnected("Alice"));

        registry.RegisterDisconnection(sid, 10);
        assertTrue(registry.isDisconnected("Alice"));

        registry.removeDisconnectedReference(10);
        assertFalse(registry.isDisconnected("Alice"));
    }

    /**
     * Verifies listener retrieval functions.
     * Ensures exceptions are thrown when connection does not implement Listener.
     */
    @Test
    void testGetListenerOfSession() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        
        DummyConnection conn = new DummyConnection();
        String sid = registry.registerConnection(conn);
        assertThrows(IllegalStateException.class, () -> registry.getListenerOf(sid));

        DummyListener listener = new DummyListener();
        String sidListener = registry.registerConnection(listener);
        assertEquals(listener, registry.getListenerOf(sidListener));
    }

    /**
     * Verifies session snapshotting behaves safely.
     * Validates fields mapped onto the immutable snapshots.
     */
    @Test
    void testSessionSnapshots() {
        ServerSessionRegistry registry = new ServerSessionRegistry();
        DummyConnection conn = new DummyConnection();

        String sid1 = registry.registerConnection(conn);
        registry.authenticate(sid1, "Alice");
        registry.enterLobby(sid1, 100);

        String sid2 = registry.registerConnection(conn);
        registry.authenticate(sid2, "Bob");
        registry.enterGame(sid2, 200);

        SessionSnapshot snap1 = registry.snapshotOf(sid1);
        assertEquals(sid1, snap1.sessionId());
        assertEquals("Alice", snap1.user().getNickname());
        assertEquals(Phase.LOBBY, snap1.phase());
        assertEquals(100, snap1.lobbyId());
        assertNull(snap1.gameId());
        assertFalse(snap1.timedOut());

        List<SessionSnapshot> allSnaps = registry.snapshots();
        assertEquals(2, allSnaps.size());

        List<SessionSnapshot> lobbySnaps = registry.sessionsInLobby(100);
        assertEquals(1, lobbySnaps.size());
        assertEquals(sid1, lobbySnaps.get(0).sessionId());

        List<SessionSnapshot> gameSnaps = registry.sessionsInGame(200);
        assertEquals(1, gameSnaps.size());
        assertEquals(sid2, gameSnaps.get(0).sessionId());
    }

    private static class DummyConnection implements ClientConnection {
        @Override
        public void sendPing() {}
        @Override
        public void close() throws IOException {}
    }

    private static class DummyListener implements ClientConnection, Listener {
        @Override
        public void sendPing() {}
        @Override
        public void close() throws IOException {}
        @Override
        public void notifyUpdate(Object update) {}
    }
}
