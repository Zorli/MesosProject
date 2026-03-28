package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static it.polimi.ingsoft.mesos.server.model.lobby.LobbyTestUsers.userWithNickname;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyTest {

    @Test
    void constructorAddsHostAndStoresChosenCapacity() {
        User host = userWithNickname("Alice");

        Lobby lobby = new Lobby(1, host, 3);

        assertEquals(1, lobby.getId());
        assertEquals("Alice", lobby.getHostNickname());
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
        assertEquals(3, lobby.getChosenCapacity());
        assertEquals(List.of(host), lobby.getUsers());
        assertFalse(lobby.canStart());
    }

    @Test
    void constructorRejectsNullHostAndInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, null, 2));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, userWithNickname("Alice"), 1));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, userWithNickname("Alice"), 6));
    }

    @Test
    void addUserRejectsDuplicateNicknameAndLobbyOverflow() {
        Lobby lobby = new Lobby(1, userWithNickname("Alice"), 2);

        assertThrows(IllegalStateException.class, () -> lobby.addUser(userWithNickname("Alice")));

        lobby.addUser(userWithNickname("Bob"));

        assertTrue(lobby.isFull());
        assertThrows(IllegalStateException.class, () -> lobby.addUser(userWithNickname("Carol")));
    }

    @Test
    void canStartBecomesTrueOnlyWhenChosenCapacityIsReached() {
        Lobby lobby = new Lobby(1, userWithNickname("Alice"), 3);

        lobby.addUser(userWithNickname("Bob"));
        assertFalse(lobby.canStart());

        lobby.addUser(userWithNickname("Carol"));
        assertTrue(lobby.canStart());
    }

    @Test
    void removeUserTransfersHostAndClearsHostWhenLobbyBecomesEmpty() {
        Lobby lobby = new Lobby(1, userWithNickname("Alice"), 2);
        User bob = userWithNickname("Bob");

        lobby.addUser(bob);
        lobby.removeUser(userWithNickname("Alice"));

        assertEquals("Bob", lobby.getHostNickname());

        lobby.removeUser(bob);

        assertNull(lobby.getHostNickname());
        assertTrue(lobby.getUsers().isEmpty());
    }

    @Test
    void resumableLobbyStartsWithoutConnectedUsersAndTracksReservedRoster() {
        Lobby lobby = Lobby.resumable(4, "game-42", List.of("Alice", "Bob", "Carol"));

        assertTrue(lobby.isResumable());
        assertEquals("game-42", lobby.getResumableGameId());
        assertEquals(LobbyStatus.RESUMABLE, lobby.getStatus());
        assertTrue(lobby.getUsers().isEmpty());
        assertEquals(List.of("Alice", "Bob", "Carol"), lobby.getReservedNicknames());
        assertEquals(3, lobby.getChosenCapacity());
    }

    @Test
    void resumableLobbyValidationRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, " ", List.of("Alice", "Bob")));
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, "game-42", List.of("Alice")));
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, "game-42", List.of("Alice", "Alice")));
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, "game-42", List.of("Alice", " ")));
        assertThrows(IllegalArgumentException.class, () -> Lobby.resumable(1, "game-42", List.of("A", "B", "C", "D", "E", "F")));
    }

    @Test
    void resumableLobbyAcceptsOnlyOriginalUsersAndAssignsHostOnFirstReconnect() {
        Lobby lobby = Lobby.resumable(4, "game-42", List.of("Alice", "Bob"));

        lobby.addUser(userWithNickname("Alice"));

        assertEquals("Alice", lobby.getHostNickname());
        assertThrows(IllegalStateException.class, () -> lobby.addUser(userWithNickname("Carol")));
    }

    @Test
    void resumableLobbyCanStartOnlyWhenAllOriginalUsersReturned() {
        Lobby lobby = Lobby.resumable(4, "game-42", List.of("Alice", "Bob", "Carol"));

        lobby.addUser(userWithNickname("Alice"));
        lobby.addUser(userWithNickname("Bob"));
        assertFalse(lobby.canStart());

        lobby.addUser(userWithNickname("Carol"));
        assertTrue(lobby.canStart());
    }

    @Test
    void setStatusRejectsNullAndGetUsersReturnsImmutableSnapshot() {
        Lobby lobby = new Lobby(1, userWithNickname("Alice"), 2);
        List<User> users = lobby.getUsers();

        assertThrows(UnsupportedOperationException.class, () -> users.add(userWithNickname("Bob")));
        assertThrows(IllegalArgumentException.class, () -> lobby.setStatus(null));
    }

    @Test
    void isHostAndTransferHostHandleNullAndEmptyLobby() {
        Lobby lobby = new Lobby(1, userWithNickname("Alice"), 2);

        assertFalse(lobby.isHost(null));
        assertFalse(lobby.isHost(userWithNickname("Bob")));
        assertTrue(lobby.isHost(userWithNickname("Alice")));

        lobby.removeUser(userWithNickname("Alice"));

        assertThrows(IllegalStateException.class, lobby::transferHostToSecondUser);
    }

    @Test
    void toStringContainsLobbyIdentityAndStatus() {
        Lobby lobby = Lobby.resumable(8, "game-42", List.of("Alice", "Bob"));

        String description = lobby.toString();

        assertTrue(description.contains("id=8"));
        assertTrue(description.contains("status=RESUMABLE"));
        assertTrue(description.contains("game-42"));
    }
}
