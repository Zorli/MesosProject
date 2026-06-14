package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

/**
 * Marker interface for all client-to-server payloads related to the lobby room phase.
 * <p>
 * Implementing classes carry data for actions performed inside a lobby,
 * such as leaving the lobby or initiating the match start.
 */
public interface LobbyClientPayload extends ClientPayload {
}
