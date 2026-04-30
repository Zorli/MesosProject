package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTest {

    @Test
    void constructorStoresMinimalLobbyState() {
        Lobby lobby = new Lobby(1, "Alice", 3);

        assertEquals(1, lobby.getId());
        assertEquals("Alice", lobby.getHostNickname());
        assertEquals(List.of("Alice"), lobby.getPlayers());
        assertEquals(3, lobby.getChosenCapacity());
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
    }

    @Test
    void constructorRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, " ", 2));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, "Alice", 1));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, "Alice", 6));
    }

    @Test
    void addPlayerRejectsDuplicateAndOverflow() {
        Lobby lobby = new Lobby(1, "Alice", 2);

        assertThrows(IllegalStateException.class, () -> lobby.addPlayer("Alice"));

        lobby.addPlayer("Bob");

        assertTrue(lobby.isFull());
        assertThrows(IllegalStateException.class, () -> lobby.addPlayer("Carol"));
    }

    @Test
    void removePlayerTransfersHostAndClearsHostWhenEmpty() {
        Lobby lobby = new Lobby(1, "Alice", 2);
        lobby.addPlayer("Bob");

        lobby.removePlayer("Alice");

        assertEquals("Bob", lobby.getHostNickname());

        lobby.removePlayer("Bob");

        assertNull(lobby.getHostNickname());
        assertTrue(lobby.isEmpty());
    }

    @Test
    void canStartBecomesTrueOnlyAtCapacity() {
        Lobby lobby = new Lobby(1, "Alice", 3);

        lobby.addPlayer("Bob");
        assertFalse(lobby.canStart());

        lobby.addPlayer("Carol");
        assertTrue(lobby.canStart());
    }

    @Test
    void playersSnapshotIsImmutable() {
        Lobby lobby = new Lobby(1, "Alice", 2);
        List<String> players = lobby.getPlayers();

        assertThrows(UnsupportedOperationException.class, () -> players.add("Bob"));
    }

    @Test
    void statusRejectsNull() {
        Lobby lobby = new Lobby(1, "Alice", 2);

        assertThrows(IllegalArgumentException.class, () -> lobby.setStatus(null));
    }
}
