package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying all domain logic, constraints, and observer notifications of the {@link Lobby} class.
 *
 * <p>Every test method is documented explaining the exact behavior under verification, the assertions,
 * and how the test verifies the outcome.
 */
class LobbyTest {

    private Lobby lobby;
    private MockListener hostListener;
    private MockListener secondListener;

    @BeforeEach
    void setUp() {
        hostListener = new MockListener();
        secondListener = new MockListener();
        lobby = new Lobby(1, "Alice", 3);
        lobby.addListener(hostListener);
    }

    /**
     * Test name: constructorInitializesCorrectly
     * 1. This test verifies that the Lobby constructor successfully sets initial attributes and fields.
     * 2. It asserts that the lobby ID, capacity, default WAITING status, host assignment, and first player list are correct.
     */
    @Test
    void constructorInitializesCorrectly() {
        assertEquals(1, lobby.getId());
        assertEquals(3, lobby.getChosenCapacity());
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
        assertEquals("Alice", lobby.getHostNickname());
        assertTrue(lobby.isHost("Alice"));
        assertFalse(lobby.isHost("Bob"));
        assertTrue(lobby.hasPlayer("Alice"));
        assertEquals(1, lobby.getPlayers().size());
        assertFalse(lobby.isEmpty());
        assertFalse(lobby.isFull());
        assertFalse(lobby.canStart());
    }

    /**
     * Test name: constructorValidatesCapacityBounds
     * 1. This test verifies that the Lobby constructor rejects invalid capacities outside the [2, 5] range.
     * 2. It asserts that constructing a lobby with capacity 1 or 6 throws an IllegalArgumentException.
     */
    @Test
    void constructorValidatesCapacityBounds() {
        assertThrows(IllegalArgumentException.class, () -> new Lobby(2, "Alice", 1));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(3, "Alice", 6));
        assertDoesNotThrow(() -> new Lobby(4, "Alice", 2));
        assertDoesNotThrow(() -> new Lobby(5, "Alice", 5));
    }

    /**
     * Test name: constructorValidatesNickname
     * 1. This test verifies that the Lobby constructor rejects null or blank host nicknames.
     * 2. It asserts that providing a null, empty, or whitespace-only nickname throws an IllegalArgumentException.
     */
    @Test
    void constructorValidatesNickname() {
        assertThrows(IllegalArgumentException.class, () -> new Lobby(2, null, 3));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(3, "", 3));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(4, "   ", 3));
    }

    /**
     * Test name: addPlayerSuccessfully
     * 1. This test verifies that adding a new valid player to the lobby succeeds and transitions status when full.
     * 2. It asserts that the player is added to the roster, the observer registers, and status transitions to READY.
     */
    @Test
    void addPlayerSuccessfully() {
        assertTrue(lobby.canBeJoinedBy("Bob"));
        boolean successBob = lobby.addPlayer("Bob", secondListener);
        assertTrue(successBob);
        assertEquals(2, lobby.getPlayers().size());
        assertTrue(lobby.hasPlayer("Bob"));
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());

        // Join 3rd player to reach capacity (3)
        MockListener thirdListener = new MockListener();
        assertTrue(lobby.canBeJoinedBy("Charlie"));
        boolean successCharlie = lobby.addPlayer("Charlie", thirdListener);
        assertTrue(successCharlie);
        assertEquals(3, lobby.getPlayers().size());
        assertTrue(lobby.isFull());
        assertTrue(lobby.canStart());
        assertEquals(LobbyStatus.READY, lobby.getStatus());
        assertFalse(lobby.canBeJoinedBy("David"));
    }

    /**
     * Test name: addPlayerRejectsDuplicateNickname
     * 1. This test verifies that joining with a nickname already present in the lobby is rejected.
     * 2. It asserts that addPlayer returns false and enqueues a class simple-name error payload to the requester.
     */
    @Test
    void addPlayerRejectsDuplicateNickname() {
        boolean success = lobby.addPlayer("Alice", secondListener);
        assertFalse(success);
        assertEquals(1, lobby.getPlayers().size());
        assertEquals(1, secondListener.receivedPayloads.size());
        assertTrue(secondListener.receivedPayloads.get(0) instanceof ErrorPayload);
        ErrorPayload error = (ErrorPayload) secondListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("User already present in lobby"));
    }

    /**
     * Test name: addPlayerRejectsWhenFull
     * 1. This test verifies that players cannot join a lobby that has already reached its capacity.
     * 2. It asserts that adding a player beyond capacity returns false and triggers a unicast error payload to the client.
     */
    @Test
    void addPlayerRejectsWhenFull() {
        lobby.addPlayer("Bob", secondListener);
        MockListener thirdListener = new MockListener();
        lobby.addPlayer("Charlie", thirdListener);
        assertTrue(lobby.isFull());

        MockListener fourthListener = new MockListener();
        assertFalse(lobby.canBeJoinedBy("David"));
        boolean success = lobby.addPlayer("David", fourthListener);
        assertFalse(success);
        assertEquals(3, lobby.getPlayers().size());
        assertEquals(1, fourthListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) fourthListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertEquals("Lobby is full", error.message);
    }

    /**
     * Test name: addPlayerRejectsWhenInvalidStatus
     * 1. This test verifies that players cannot join a lobby if the game has already started or if it is closed.
     * 2. It asserts that attempting to join returns false and sends an IllegalStateException payload to the requester.
     */
    @Test
    void addPlayerRejectsWhenInvalidStatus() {
        // Start the lobby to change status to STARTED
        lobby.addPlayer("Bob", secondListener);
        lobby.addPlayer("Charlie", new MockListener());
        lobby.startMatch("Alice");
        assertEquals(LobbyStatus.STARTED, lobby.getStatus());

        MockListener fourthListener = new MockListener();
        assertFalse(lobby.canBeJoinedBy("David"));
        boolean success = lobby.addPlayer("David", fourthListener);
        assertFalse(success);
        assertEquals(1, fourthListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) fourthListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("Cannot join lobby with status"));
    }

    /**
     * Test name: removePlayerSuccessfully
     * 1. This test verifies that removing a player successfully updates the lobby state, host status, and notifies listeners.
     * 2. It asserts that the player count decreases, the host is reassigned if the original host leaves, and listener lists update.
     */
    @Test
    void removePlayerSuccessfully() {
        lobby.addPlayer("Bob", secondListener);
        
        // Remove Bob (non-host)
        boolean successRemoveBob = lobby.removePlayer("Bob", secondListener);
        assertTrue(successRemoveBob);
        assertEquals(1, lobby.getPlayers().size());
        assertFalse(lobby.hasPlayer("Bob"));
        
        // Bob is removed from listener notifications, so we trigger a broadcast and check that he doesn't receive it
        lobby.notifyPlayerJoined("Charlie");
        assertEquals(0, secondListener.receivedPayloads.size()); // Bob left, so he gets no updates
        
        // Add Bob again to test host delegation
        lobby.addPlayer("Bob", secondListener);
        assertEquals("Alice", lobby.getHostNickname());
        
        // Remove Alice (current host)
        boolean successRemoveAlice = lobby.removePlayer("Alice", hostListener);
        assertTrue(successRemoveAlice);
        assertEquals("Bob", lobby.getHostNickname()); // Host delegated to Bob
    }

    /**
     * Test name: removePlayerTransitionsToClosedWhenEmpty
     * 1. This test verifies that removing the last player in the lobby transitions its status to CLOSED.
     * 2. It asserts that the lobby status changes to CLOSED, and cannot be joined anymore.
     */
    @Test
    void removePlayerTransitionsToClosedWhenEmpty() {
        boolean success = lobby.removePlayer("Alice", hostListener);
        assertTrue(success);
        assertTrue(lobby.isEmpty());
        assertEquals(LobbyStatus.CLOSED, lobby.getStatus());
        assertFalse(lobby.canBeJoinedBy("Bob"));
    }

    /**
     * Test name: removePlayerRevertsStatusToWaiting
     * 1. This test verifies that removing a player from a READY lobby transitions its status back to WAITING.
     * 2. It asserts that the status transitions from READY to WAITING because capacity is no longer satisfied.
     */
    @Test
    void removePlayerRevertsStatusToWaiting() {
        lobby.addPlayer("Bob", secondListener);
        MockListener thirdListener = new MockListener();
        lobby.addPlayer("Charlie", thirdListener);
        assertEquals(LobbyStatus.READY, lobby.getStatus());

        boolean success = lobby.removePlayer("Charlie", thirdListener);
        assertTrue(success);
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
    }

    /**
     * Test name: removePlayerRejectsNonExistent
     * 1. This test verifies that removing a player who is not in the lobby fails.
     * 2. It asserts that removePlayer returns false and broadcasts the IllegalStateException error payload (as target is null).
     */
    @Test
    void removePlayerRejectsNonExistent() {
        boolean success = lobby.removePlayer("Bob", secondListener);
        assertFalse(success);
        assertEquals(1, hostListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) hostListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("User not present in lobby"));
    }

    /**
     * Test name: startMatchSuccessfully
     * 1. This test verifies that the host can start the match when the lobby status is READY.
     * 2. It asserts that the status changes to STARTED and startMatch returns true.
     */
    @Test
    void startMatchSuccessfully() {
        lobby.addPlayer("Bob", secondListener);
        lobby.addPlayer("Charlie", new MockListener());
        assertEquals(LobbyStatus.READY, lobby.getStatus());

        boolean success = lobby.startMatch("Alice");
        assertTrue(success);
        assertEquals(LobbyStatus.STARTED, lobby.getStatus());
    }

    /**
     * Test name: startMatchRejectsNonHost
     * 1. This test verifies that a non-host player cannot start the match.
     * 2. It asserts that startMatch returns false and broadcasts an error payload to all listeners.
     */
    @Test
    void startMatchRejectsNonHost() {
        lobby.addPlayer("Bob", secondListener);
        lobby.addPlayer("Charlie", new MockListener());

        boolean success = lobby.startMatch("Bob");
        assertFalse(success);
        assertNotEquals(LobbyStatus.STARTED, lobby.getStatus());
        
        // Assert error is broadcast
        assertEquals(1, hostListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) hostListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("Only the lobby host can start the match"));
    }

    /**
     * Test name: startMatchRejectsWhenNotReady
     * 1. This test verifies that the host cannot start the match if the lobby is not full yet.
     * 2. It asserts that startMatch returns false and broadcasts an error payload.
     */
    @Test
    void startMatchRejectsWhenNotReady() {
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
        boolean success = lobby.startMatch("Alice");
        assertFalse(success);
        assertNotEquals(LobbyStatus.STARTED, lobby.getStatus());

        assertEquals(1, hostListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) hostListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("Cannot start lobby with status"));
    }

    /**
     * Test name: rejoinResumablePlayerFailsWhenNotResumable
     * 1. This test verifies that a player cannot rejoin as a resumable player if the lobby status is not RESUMABLE.
     * 2. It asserts that rejoinResumablePlayer returns false and unicasts an error payload to the requester.
     */
    @Test
    void rejoinResumablePlayerFailsWhenNotResumable() {
        boolean success = lobby.rejoinResumablePlayer("Alice", secondListener);
        assertFalse(success);
        assertEquals(1, secondListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) secondListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("Cannot resume lobby with status"));
    }

    /**
     * Test name: reportPhaseErrorSendsPayload
     * 1. This test verifies that calling reportPhaseError on a listener correctly transmits a PHASE_ERROR payload.
     * 2. It asserts that the listener receives exactly one ErrorPayload of type "PHASE_ERROR".
     */
    @Test
    void reportPhaseErrorSendsPayload() {
        lobby.reportPhaseError(secondListener);
        assertEquals(1, secondListener.receivedPayloads.size());
        assertTrue(secondListener.receivedPayloads.get(0) instanceof ErrorPayload);
        ErrorPayload error = (ErrorPayload) secondListener.receivedPayloads.get(0);
        assertEquals("PHASE_ERROR", error.code);
    }

    /**
     * Test name: notificationsPublishCorrectPayloads
     * 1. This test verifies that join, exit, and start notifications trigger correct updates to listeners.
     * 2. It asserts that listeners receive LobbyUpdatePayload objects with correct event types.
     */
    @Test
    void notificationsPublishCorrectPayloads() {
        lobby.notifyPlayerJoined("Bob");
        assertEquals(1, hostListener.receivedPayloads.size());
        assertTrue(hostListener.receivedPayloads.get(0) instanceof LobbyUpdatePayload);
        LobbyUpdatePayload payload = (LobbyUpdatePayload) hostListener.receivedPayloads.get(0);
        assertEquals(LobbyEventType.LOBBY_JOINED, payload.event);
        assertEquals("Bob", payload.nickname);

        lobby.notifyPlayerLeft("Bob", secondListener);
        // hostListener gets the update via broadcast
        assertEquals(2, hostListener.receivedPayloads.size());
        // secondListener gets the update via direct unicast
        assertEquals(1, secondListener.receivedPayloads.size());

        lobby.notifyMatchStarted("Alice", secondListener);
        assertEquals(2, secondListener.receivedPayloads.size());
        LobbyUpdatePayload startPayload = (LobbyUpdatePayload) secondListener.receivedPayloads.get(1);
        assertEquals(LobbyEventType.LOBBY_STARTED, startPayload.event);
    }

    /**
     * Test name: clearListenersRemovesAllObservers
     * 1. This test verifies that clearListeners deletes all registered listeners from the lobby.
     * 2. It asserts that notifications triggered after clearListeners are not received by any mock listeners.
     */
    @Test
    void clearListenersRemovesAllObservers() {
        lobby.clearListeners();
        lobby.notifyPlayerJoined("Bob");
        assertEquals(0, hostListener.receivedPayloads.size());
    }

    /**
     * Test name: testRejoinResumablePlayerSuccessfully
     * 1. This test verifies that an expected player can rejoin the lobby when it is in RESUMABLE status.
     * 2. It asserts that rejoining Bob succeeds, Bob is in the players list, and no errors are unicast.
     */
    @Test
    void testRejoinResumablePlayerSuccessfully() throws Exception {
        java.lang.reflect.Field statusField = Lobby.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(lobby, LobbyStatus.RESUMABLE);

        java.lang.reflect.Field rosterField = Lobby.class.getDeclaredField("resumableRoster");
        rosterField.setAccessible(true);
        rosterField.set(lobby, List.of("Alice", "Bob"));

        assertTrue(lobby.isExpectedResumePlayer("Bob"));
        assertTrue(lobby.canBeJoinedBy("Bob"));

        boolean success = lobby.rejoinResumablePlayer("Bob", secondListener);
        assertTrue(success);
        assertTrue(lobby.hasPlayer("Bob"));
        assertEquals(0, secondListener.receivedPayloads.size());
    }

    /**
     * Test name: testRejoinResumablePlayerFailsWhenNotExpected
     * 1. This test verifies that a player not in the resumable roster cannot rejoin the lobby.
     * 2. It asserts that rejoining Charlie fails, and a class simple-name exception payload is unicast to him.
     */
    @Test
    void testRejoinResumablePlayerFailsWhenNotExpected() throws Exception {
        java.lang.reflect.Field statusField = Lobby.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(lobby, LobbyStatus.RESUMABLE);

        java.lang.reflect.Field rosterField = Lobby.class.getDeclaredField("resumableRoster");
        rosterField.setAccessible(true);
        rosterField.set(lobby, List.of("Alice", "Bob"));

        assertFalse(lobby.isExpectedResumePlayer("Charlie"));
        assertFalse(lobby.canBeJoinedBy("Charlie"));

        boolean success = lobby.rejoinResumablePlayer("Charlie", secondListener);
        assertFalse(success);
        assertEquals(1, secondListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) secondListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("User not allowed to resume this lobby"));
    }

    /**
     * Test name: testRejoinResumablePlayerFailsWhenAlreadyJoined
     * 1. This test verifies that a player already in the lobby cannot rejoin it under resumable state.
     * 2. It asserts that rejoining Alice (who is already in players) fails and unicasts an exception payload.
     */
    @Test
    void testRejoinResumablePlayerFailsWhenAlreadyJoined() throws Exception {
        java.lang.reflect.Field statusField = Lobby.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(lobby, LobbyStatus.RESUMABLE);

        java.lang.reflect.Field rosterField = Lobby.class.getDeclaredField("resumableRoster");
        rosterField.setAccessible(true);
        rosterField.set(lobby, List.of("Alice", "Bob"));

        assertTrue(lobby.isExpectedResumePlayer("Alice"));
        assertFalse(lobby.canBeJoinedBy("Alice")); // already joined

        boolean success = lobby.rejoinResumablePlayer("Alice", secondListener);
        assertFalse(success);
        assertEquals(1, secondListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) secondListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("User already present in lobby"));
    }

    /**
     * Test name: testStartMatchThrowsExceptionWhenCannotStart
     * 1. This test verifies that trying to start a match when players count does not match capacity is rejected.
     * 2. It asserts that calling startMatch triggers the internally handled exception and broadcasts the error payload.
     */
    @Test
    void testStartMatchThrowsExceptionWhenCannotStart() throws Exception {
        java.lang.reflect.Field statusField = Lobby.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(lobby, LobbyStatus.READY);

        // Capacity is 3, but only Alice (1 player) is in players. So canStart() is false.
        boolean success = lobby.startMatch("Alice");
        assertFalse(success);

        assertEquals(1, hostListener.receivedPayloads.size());
        ErrorPayload error = (ErrorPayload) hostListener.receivedPayloads.get(0);
        assertEquals("IllegalStateException", error.code);
        assertTrue(error.message.contains("Cannot start lobby that cannot start"));
    }

    /**
     * Helper Mock class implementing the Listener interface to collect payloads in tests.
     */
    private static class MockListener implements Listener {
        final List<Object> receivedPayloads = new ArrayList<>();

        @Override
        public void notifyUpdate(Object payload) {
            receivedPayloads.add(payload);
        }
    }
}
