package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.Player;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTest {

    @Test
    void constructorAddsHostAndKeepsLobbyWaiting() {
        Player host = playerWithNickname("Alice");

        Lobby lobby = new Lobby(1, host);

        assertEquals(1, lobby.getId());
        assertEquals("Alice", lobby.getHostNickname());
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
        assertEquals(1, lobby.getPlayers().size());
        assertEquals(host, lobby.getPlayers().get(0));
    }

    @Test
    void constructorThrowsWhenHostIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, null));
    }

    @Test
    void constructorThrowsWhenHostNicknameIsBlank() {
        Player host = playerWithNickname("   ");

        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, host));
    }

    @Test
    void addPlayerThrowsWhenNicknameAlreadyExists() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));

        assertThrows(IllegalStateException.class, () -> lobby.addPlayer(playerWithNickname("Alice")));
    }

    @Test
    void addPlayerThrowsWhenLobbyIsFull() {
        Lobby lobby = new Lobby(1, playerWithNickname("P1"));
        lobby.addPlayer(playerWithNickname("P2"));
        lobby.addPlayer(playerWithNickname("P3"));
        lobby.addPlayer(playerWithNickname("P4"));
        lobby.addPlayer(playerWithNickname("P5"));

        assertTrue(lobby.isFull());
        assertThrows(IllegalStateException.class, () -> lobby.addPlayer(playerWithNickname("P6")));
    }

    @Test
    void canStartIsTrueWhenAtLeastTwoPlayersArePresent() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));

        assertFalse(lobby.canStart());

        lobby.addPlayer(playerWithNickname("Bob"));

        assertTrue(lobby.canStart());
    }

    @Test
    void removePlayerThrowsWhenPlayerIsNotInLobby() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));

        assertThrows(IllegalStateException.class, () -> lobby.removePlayer(playerWithNickname("Bob")));
    }

    @Test
    void transferHostAssignsFirstRemainingPlayer() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));
        Player bob = playerWithNickname("Bob");
        Player carol = playerWithNickname("Carol");

        lobby.addPlayer(bob);
        lobby.addPlayer(carol);
        lobby.removePlayer(playerWithNickname("Alice"));

        assertEquals("Bob", lobby.getHostNickname());
    }

    @Test
    void removeLastPlayerClearsHostNickname() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));

        lobby.removePlayer(playerWithNickname("Alice"));

        assertNull(lobby.getHostNickname());
    }

    private static Player playerWithNickname(String nickname) {
        return new Player(nickname);
    }
}
