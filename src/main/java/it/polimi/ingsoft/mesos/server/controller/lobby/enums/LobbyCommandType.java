package it.polimi.ingsoft.mesos.server.controller.lobby.enums;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.StartLobbyPayload;

/**
 * Defines the operations a client is permitted to perform once they are in a lobby.
 *
 * <p>This enum is used by {@link it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController}
 * to route incoming {@link ClientPayload} commands to their respective model actions.
 */
public enum LobbyCommandType {
    /** Command representing a request to start the game match (restricted to the lobby host). */
    START_LOBBY,
    /** Command representing a request to exit the current lobby. */
    EXIT_LOBBY;

    /**
     * Resolves the appropriate {@link LobbyCommandType} from a generic {@link ClientPayload}.
     *
     * @param payload the incoming payload from a client connection
     * @return the matched {@link LobbyCommandType}
     * @throws IllegalArgumentException if the payload does not map to any valid lobby command
     */
    public static LobbyCommandType fromPayload(ClientPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        return switch (payload) {
            case StartLobbyPayload ignored -> START_LOBBY;
            case ExitLobbyPayload ignored -> EXIT_LOBBY;
            default -> throw new IllegalArgumentException("Unexpected lobby payload: " + payload);
        };
    }
}
