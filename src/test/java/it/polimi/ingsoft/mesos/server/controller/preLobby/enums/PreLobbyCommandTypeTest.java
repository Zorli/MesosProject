package it.polimi.ingsoft.mesos.server.controller.preLobby.enums;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests verifying correct mappings of client payload instances
 * to PreLobbyCommandType enum constants.
 */
class PreLobbyCommandTypeTest {

    /**
     * Verifies that an AuthenticationPayload correctly maps to AUTHENTICATE.
     * Checks that fromPayload returns the expected enum constant for authentication requests.
     */
    @Test
    void testAuthenticationPayloadMapping() {
        AuthenticationPayload payload = new AuthenticationPayload("Alice");
        assertEquals(PreLobbyCommandType.AUTHENTICATE, PreLobbyCommandType.fromPayload(payload));
    }

    /**
     * Verifies that a JoinLobbyPayload correctly maps to JOIN_LOBBY.
     * Checks that fromPayload returns the expected enum constant for joining lobbies.
     */
    @Test
    void testJoinLobbyPayloadMapping() {
        JoinLobbyPayload payload = new JoinLobbyPayload(1);
        assertEquals(PreLobbyCommandType.JOIN_LOBBY, PreLobbyCommandType.fromPayload(payload));
    }

    /**
     * Verifies that a CreateLobbyPayload correctly maps to CREATE_LOBBY.
     * Checks that fromPayload returns the expected enum constant for creating lobbies.
     */
    @Test
    void testCreateLobbyPayloadMapping() {
        CreateLobbyPayload payload = new CreateLobbyPayload(4);
        assertEquals(PreLobbyCommandType.CREATE_LOBBY, PreLobbyCommandType.fromPayload(payload));
    }

    /**
     * Verifies that a ListLobbiesPayload correctly maps to LIST_LOBBIES.
     * Checks that fromPayload returns the expected enum constant for retrieving lobby list.
     */
    @Test
    void testListLobbiesPayloadMapping() {
        ListLobbiesPayload payload = new ListLobbiesPayload();
        assertEquals(PreLobbyCommandType.LIST_LOBBIES, PreLobbyCommandType.fromPayload(payload));
    }

    /**
     * Verifies that passing a null payload causes fromPayload to throw an IllegalArgumentException.
     * Validates input null safety for the payload parsing utility.
     */
    @Test
    void testNullPayloadMappingThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> PreLobbyCommandType.fromPayload(null));
        assertEquals("Payload cannot be null", ex.getMessage());
    }

    /**
     * Verifies that passing an unexpected/unrecognized client payload throws an IllegalArgumentException.
     * Validates that payload types from other phases are correctly rejected during pre-lobby.
     */
    @Test
    void testUnexpectedPayloadMappingThrows() {
        ClientPayload unexpectedPayload = new ClientPayload() {};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> PreLobbyCommandType.fromPayload(unexpectedPayload));
        assertEquals("Unexpected pre-lobby payload: " + unexpectedPayload, ex.getMessage());
    }
}
