package it.polimi.ingsoft.mesos.server.model.server;

import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying all mapping operations of the {@link PreLobbyPayloadMapper} class.
 *
 * <p>Every test method is documented explaining the exact behavior under verification, the assertions,
 * and how the test verifies the outcome.
 */
class PreLobbyPayloadMapperTest {

    private PreLobbyPayloadMapper mapper;
    private Lobby lobby;

    @BeforeEach
    void setUp() {
        mapper = new PreLobbyPayloadMapper();
        lobby = new Lobby(1, "Alice", 3);
        lobby.addPlayer("Bob", new it.polimi.ingsoft.mesos.server.virtualview.Listener() {
            @Override
            public void notifyUpdate(Object payload) {}
        });
    }

    /**
     * Test name: testAuthenticatedPayload
     * 1. This test verifies that the authenticated payload is mapped correctly.
     * 2. It asserts that the returned payload contains the correct nickname and status message.
     */
    @Test
    void testAuthenticatedPayload() {
        AuthenticationResultPayload payload = mapper.authenticated("Alice");
        assertNotNull(payload);
        assertEquals("Alice", payload.nickname);
    }

    /**
     * Test name: testLobbyCreatedPayload
     * 1. This test verifies that mapping a created lobby wraps the state into a LOBBY_CREATED event payload.
     * 2. It asserts that the host nickname, lobby ID, and player list match the lobby configuration.
     */
    @Test
    void testLobbyCreatedPayload() {
        LobbyUpdatePayload payload = mapper.lobbyCreated("Alice", lobby);
        assertNotNull(payload);
        assertEquals(LobbyEventType.LOBBY_CREATED, payload.event);
        assertEquals("Alice", payload.nickname);
        assertEquals(1, payload.lobbyId);
        assertEquals(2, payload.players.size());
    }

    /**
     * Test name: testLobbyListPayload
     * 1. This test verifies that mapping a list of open lobbies formats summaries of all lobbies correctly.
     * 2. It asserts that the resulting LobbyListPayload contains summaries reflecting the input list size and properties.
     */
    @Test
    void testLobbyListPayload() {
        LobbyListPayload payload = mapper.lobbyList(List.of(lobby));
        assertNotNull(payload);
        assertEquals(1, payload.lobbies.size());
        LobbySummaryPayload summary = payload.lobbies.get(0);
        assertEquals(1, summary.lobbyId);
        assertEquals("Alice", summary.hostNickname);
        assertEquals(2, summary.currentPlayers);
        assertEquals(3, summary.maxPlayers);
    }

    /**
     * Test name: testSummaryPayload
     * 1. This test verifies that mapping a single lobby into a summary payload populates all attributes correctly.
     * 2. It asserts that the resulting summary matches the ID, host, players count, and max players of the lobby.
     */
    @Test
    void testSummaryPayload() {
        LobbySummaryPayload summary = mapper.summary(lobby);
        assertNotNull(summary);
        assertEquals(1, summary.lobbyId);
        assertEquals("Alice", summary.hostNickname);
        assertEquals(2, summary.currentPlayers);
        assertEquals(3, summary.maxPlayers);
        assertEquals(lobby.getStatus().name(), summary.status);
    }
}
