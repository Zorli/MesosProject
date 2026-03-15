package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.Orchestration.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.polimi.ingsoft.mesos.server.model.lobby.LobbyTestPlayers.playerWithNickname;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyManagerTest {

    @Test
    void constructorThrowsWhenGameFactoryIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new LobbyManager(null));
    }

    @Test
    void createLobbyStoresLobbyAndAssignsIncrementalIds() {
        LobbyManager manager = new LobbyManager();

        Lobby firstLobby = manager.createLobby(playerWithNickname("Alice"));
        Lobby secondLobby = manager.createLobby(playerWithNickname("Bob"));

        assertEquals(1, firstLobby.getId());
        assertEquals(2, secondLobby.getId());
        assertTrue(manager.getLobby(1).isPresent());
        assertTrue(manager.getLobby(2).isPresent());
    }

    @Test
    void joinLobbySetsStatusToReadyWhenLobbyCanStart() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));

        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void removeHostDestroysLobbyWhenHostWasAlone() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Lobby lobby = manager.createLobby(host);

        manager.removePlayer(lobby.getId(), host);

        assertTrue(manager.getLobby(lobby.getId()).isEmpty());
    }

    @Test
    void removeHostTransfersHostToSecondPlayerWhenLobbyIsNotEmpty() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);

        manager.removePlayer(lobby.getId(), host);

        Lobby updatedLobby = manager.getLobby(lobby.getId()).orElseThrow();
        assertEquals("Bob", updatedLobby.getHostNickname());
    }

    @Test
    void removePlayerSetsStatusBackToWaitingWhenBelowMinimum() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);

        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());

        manager.removePlayer(lobby.getId(), secondPlayer);

        assertEquals(LobbyStatus.WAITING, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void joinLobbyThrowsWhenLobbyIsStarted() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        lobby.setStatus(LobbyStatus.STARTED);

        assertThrows(IllegalStateException.class,
                () -> manager.joinLobby(lobby.getId(), playerWithNickname("Bob")));
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyIsAlreadyStarted() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));
        lobby.setStatus(LobbyStatus.STARTED);

        assertThrows(IllegalStateException.class, () -> manager.createGameFromLobby(lobby.getId()));
    }

    @Test
    void createGameFromLobbyReturnsGameOrchestratorAndStartsLobby() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        GameOrchestrator gameOrchestrator = manager.createGameFromLobby(lobby.getId());

        assertInstanceOf(GameOrchestrator.class, gameOrchestrator);
        assertEquals(LobbyStatus.STARTED, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyDoesNotExist() {
        LobbyManager manager = new LobbyManager();

        assertThrows(IllegalArgumentException.class, () -> manager.createGameFromLobby(999));
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyIsWaiting() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));

        assertThrows(IllegalStateException.class, () -> manager.createGameFromLobby(lobby.getId()));
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyIsClosed() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Lobby lobby = manager.createLobby(host);
        manager.removePlayer(lobby.getId(), host);

        assertThrows(IllegalArgumentException.class, () -> manager.createGameFromLobby(lobby.getId()));
    }

    @Test
    void createGameFromLobbyUsesInjectedFactoryAndPassesLobbyPlayersInOrder() {
        RecordingGameFactory gameFactory = new RecordingGameFactory();
        LobbyManager manager = new LobbyManager(gameFactory);
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);
        List<Player> expectedPlayers = lobby.getPlayers();

        GameOrchestrator gameOrchestrator = manager.createGameFromLobby(lobby.getId());

        assertInstanceOf(GameOrchestrator.class, gameOrchestrator);
        assertEquals(1, gameFactory.createGameCalls);
        assertEquals(expectedPlayers, gameFactory.capturedPlayers);
        assertNotSame(expectedPlayers, gameFactory.capturedPlayers);
        assertEquals(LobbyStatus.STARTED, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void createGameFromLobbyKeepsLobbyReadyWhenFactoryFails() {
        RecordingGameFactory gameFactory = new RecordingGameFactory();
        gameFactory.failure = new IllegalStateException("Factory failure");
        LobbyManager manager = new LobbyManager(gameFactory);
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> manager.createGameFromLobby(lobby.getId())
        );

        assertEquals("Factory failure", exception.getMessage());
        assertEquals(1, gameFactory.createGameCalls);
        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void listOpenLobbiesExcludesStartedAndClosedLobbies() {
        LobbyManager manager = new LobbyManager();

        Lobby waitingLobby = manager.createLobby(playerWithNickname("Alice"));
        Lobby startedLobby = manager.createLobby(playerWithNickname("Bob"));
        startedLobby.setStatus(LobbyStatus.STARTED);

        Lobby closedLobby = manager.createLobby(playerWithNickname("Carol"));
        closedLobby.setStatus(LobbyStatus.CLOSED);

        List<Lobby> openLobbies = manager.listOpenLobbies();

        assertEquals(1, openLobbies.size());
        assertEquals(waitingLobby.getId(), openLobbies.get(0).getId());
    }

    @Test
    void getLobbyReturnsEmptyWhenLobbyDoesNotExist() {
        LobbyManager manager = new LobbyManager();

        assertFalse(manager.getLobby(999).isPresent());
    }

    private static final class RecordingGameFactory implements GameFactory {
        private int createGameCalls;
        private ArrayList<Player> capturedPlayers;
        private RuntimeException failure;

        @Override
        public Game createGame(ArrayList<Player> players) {
            this.createGameCalls++;
            this.capturedPlayers = new ArrayList<>(players);

            if (this.failure != null) {
                throw this.failure;
            }

            return null;
        }
    }
}
