package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.Player;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyManagerTest {

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

    private static Player playerWithNickname(String nickname) {
        Player player = new Player();

        try {
            Field nicknameField = Player.class.getDeclaredField("nickname");
            nicknameField.setAccessible(true);
            nicknameField.set(player, nickname);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }

        return player;
    }
}
