package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import org.junit.jupiter.api.Test;

import static it.polimi.ingsoft.mesos.server.model.lobby.LobbyTestPlayers.playerWithNickname;
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

    @Test
    void resumableLobbyStartsWithoutConnectedPlayersAndTracksReservedRoster() {
        Lobby lobby = Lobby.resumable(4, "game-42", java.util.List.of("Alice", "Bob", "Carol"));

        assertTrue(lobby.isResumable());
        assertEquals("game-42", lobby.getResumableGameId());
        assertEquals(LobbyStatus.RESUMABLE, lobby.getStatus());
        assertTrue(lobby.getPlayers().isEmpty());
        assertEquals(java.util.List.of("Alice", "Bob", "Carol"), lobby.getReservedPlayerNicknames());
    }

    @Test
    void resumableLobbyAcceptsOnlyOriginalPlayers() {
        Lobby lobby = Lobby.resumable(4, "game-42", java.util.List.of("Alice", "Bob"));

        lobby.addPlayer(playerWithNickname("Alice"));

        assertThrows(IllegalStateException.class, () -> lobby.addPlayer(playerWithNickname("Carol")));
    }

    @Test
    void resumableLobbyCanStartOnlyWhenAllOriginalPlayersReturned() {
        Lobby lobby = Lobby.resumable(4, "game-42", java.util.List.of("Alice", "Bob", "Carol"));

        lobby.addPlayer(playerWithNickname("Alice"));
        lobby.addPlayer(playerWithNickname("Bob"));
        assertFalse(lobby.canStart());

        lobby.addPlayer(playerWithNickname("Carol"));
        assertTrue(lobby.canStart());
    }

    @Test
    void resumableLobbyValidationRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, " ", java.util.List.of("Alice", "Bob")));
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, "game-42", java.util.List.of("Alice")));
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, "game-42", java.util.List.of("Alice", "Alice")));
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, "game-42", java.util.List.of("A", "B", "C", "D", "E", "F")));
    }

    @Test
    void setStatusRejectsNullAndIsHostHandlesNullValues() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));

        assertFalse(lobby.isHost(null));
        assertFalse(lobby.isHost(playerWithNickname("Bob")));
        assertTrue(lobby.isHost(playerWithNickname("Alice")));
        assertThrows(IllegalArgumentException.class, () -> lobby.setStatus(null));
    }

    @Test
    void getPlayersReturnsImmutableSnapshot() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));
        java.util.List<Player> players = lobby.getPlayers();

        assertThrows(UnsupportedOperationException.class, () -> players.add(playerWithNickname("Bob")));
    }

    @Test
    void transferHostThrowsWhenLobbyIsEmpty() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));
        lobby.removePlayer(playerWithNickname("Alice"));

        assertThrows(IllegalStateException.class, lobby::transferHostToSecondPlayer);
    }

    @Test
    void addPlayerAssignsHostWhenResumableLobbyReconnectsFirstPlayer() {
        Lobby lobby = Lobby.resumable(4, "game-42", java.util.List.of("Alice", "Bob"));

        lobby.addPlayer(playerWithNickname("Alice"));

        assertEquals("Alice", lobby.getHostNickname());
    }

    @Test
    void gettersExposeConfiguredLobbyBounds() {
        Lobby lobby = new Lobby(1, playerWithNickname("Alice"));

        assertEquals(2, lobby.getMinPlayers());
        assertEquals(5, lobby.getMaxPlayers());
    }

    @Test
    void resumableLobbyAllowsExactlyFiveReservedPlayersAndBecomesFullAtBoundary() {
        Lobby lobby = Lobby.resumable(4, "game-42", java.util.List.of("A", "B", "C", "D", "E"));

        assertFalse(lobby.isFull());
        lobby.addPlayer(playerWithNickname("A"));
        lobby.addPlayer(playerWithNickname("B"));
        lobby.addPlayer(playerWithNickname("C"));
        lobby.addPlayer(playerWithNickname("D"));
        assertFalse(lobby.isFull());

        lobby.addPlayer(playerWithNickname("E"));

        assertTrue(lobby.isFull());
        assertTrue(lobby.canStart());
    }

    @Test
    void toStringContainsLobbyIdentityAndStatus() {
        Lobby lobby = Lobby.resumable(8, "game-42", java.util.List.of("Alice", "Bob"));

        String description = lobby.toString();

        assertTrue(description.contains("id=8"));
        assertTrue(description.contains("status=RESUMABLE"));
        assertTrue(description.contains("game-42"));
    }
}
