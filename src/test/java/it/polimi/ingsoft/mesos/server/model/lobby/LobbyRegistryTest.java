package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyRegistryTest {

    @Test
    void createStoresLobbyAndAssignsIncrementalIds() {
        LobbyRegistry registry = new LobbyRegistry();

        Lobby firstLobby = registry.create("Alice", 2);
        Lobby secondLobby = registry.create("Bob", 3);

        assertEquals(1, firstLobby.getId());
        assertEquals(2, secondLobby.getId());
        assertTrue(registry.get(1).isPresent());
        assertTrue(registry.get(2).isPresent());
    }

    @Test
    void removeDeletesLobby() {
        LobbyRegistry registry = new LobbyRegistry();
        Lobby lobby = registry.create("Alice", 2);

        registry.remove(lobby.getId());

        assertTrue(registry.get(lobby.getId()).isEmpty());
    }

    @Test
    void listOpenExcludesClosedAndStartedLobbies() {
        LobbyRegistry registry = new LobbyRegistry();
        Lobby waitingLobby = registry.create("Alice", 3);
        Lobby startedLobby = registry.create("Bob", 2);
        Lobby closedLobby = registry.create("Carol", 2);
        startedLobby.setStatus(LobbyStatus.STARTED);
        closedLobby.setStatus(LobbyStatus.CLOSED);

        List<Lobby> openLobbies = registry.listOpen();

        assertEquals(List.of(waitingLobby), openLobbies);
    }

    @Test
    void containsPlayerChecksOnlyOpenLobbies() {
        LobbyRegistry registry = new LobbyRegistry();
        Lobby openLobby = registry.create("Alice", 2);
        Lobby startedLobby = registry.create("Carol", 2);
        openLobby.addPlayer("Bob");
        startedLobby.setStatus(LobbyStatus.STARTED);

        assertTrue(registry.containsPlayer("Bob"));
        assertFalse(registry.containsPlayer("Carol"));
    }
}
