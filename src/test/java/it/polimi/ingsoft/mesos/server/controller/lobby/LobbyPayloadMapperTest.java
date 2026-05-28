package it.polimi.ingsoft.mesos.server.controller.lobby;

import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyPayloadMapper;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying remaining mapping operations of the {@link LobbyPayloadMapper} class.
 *
 * <p>Every test method is documented explaining the exact behavior under verification, the assertions,
 * and how the test verifies the outcome.
 */
class LobbyPayloadMapperTest {

    private LobbyPayloadMapper mapper;
    private Lobby lobby;

    @BeforeEach
    void setUp() {
        mapper = new LobbyPayloadMapper();
        lobby = new Lobby(1, "Alice", 3);
        lobby.addPlayer("Bob", new it.polimi.ingsoft.mesos.server.virtualview.Listener() {
            @Override
            public void notifyUpdate(ServerPayload payload) {}
        });
    }

    /**
     * Test name: testLobbyJoinedPayload
     * 1. This test verifies that mapping a joined event wraps the state into a LOBBY_JOINED event payload.
     * 2. It asserts that the joining nickname, lobby ID, and player list match the lobby configuration.
     */
    @Test
    void testLobbyJoinedPayload() {
        LobbyUpdatePayload payload = mapper.lobbyJoined("Bob", lobby);
        assertNotNull(payload);
        assertEquals(LobbyEventType.LOBBY_JOINED, payload.event);
        assertEquals("Bob", payload.nickname);
    }

    /**
     * Test name: testLobbyLeftPayloadWithLobby
     * 1. This test verifies that leaving a lobby when it is still open returns a left event payload populated with the lobby state.
     * 2. It asserts that the event type is LOBBY_LEFT and the player list contains the remaining players.
     */
    @Test
    void testLobbyLeftPayloadWithLobby() {
        LobbyUpdatePayload payload = mapper.lobbyLeft("Bob", lobby, 1);
        assertNotNull(payload);
        assertEquals(LobbyEventType.LOBBY_LEFT, payload.event);
        assertEquals("Bob", payload.nickname);
        assertEquals(1, payload.lobbyId);
        assertEquals(2, payload.players.size());
    }

    /**
     * Test name: testLobbyLeftPayloadWithNullLobby
     * 1. This test verifies that leaving a lobby when it is closed (null lobby model) returns a left event payload with empty lists.
     * 2. It asserts that the event type is LOBBY_LEFT and the players list is empty.
     */
    @Test
    void testLobbyLeftPayloadWithNullLobby() {
        LobbyUpdatePayload payload = mapper.lobbyLeft("Bob", null, 1);
        assertNotNull(payload);
        assertEquals(LobbyEventType.LOBBY_LEFT, payload.event);
        assertEquals("Bob", payload.nickname);
        assertEquals(1, payload.lobbyId);
        assertTrue(payload.players.isEmpty());
    }

    /**
     * Test name: testLobbyStartedPayload
     * 1. This test verifies that starting a lobby wraps the state into a LOBBY_STARTED event payload.
     * 2. It asserts that the host nickname, lobby ID, and player list match the lobby configuration.
     */
    @Test
    void testLobbyStartedPayload() {
        LobbyUpdatePayload payload = mapper.lobbyStarted("Alice", lobby);
        assertNotNull(payload);
        assertEquals(LobbyEventType.LOBBY_STARTED, payload.event);
        assertEquals("Alice", payload.nickname);
    }
}
