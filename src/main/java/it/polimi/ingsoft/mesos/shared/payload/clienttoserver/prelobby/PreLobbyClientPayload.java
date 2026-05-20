package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

/**
 * Marker interface for all client-to-server payloads related to the pre-lobby phase.
 * <p>
 * Implementing classes carry data for actions performed before a player is in a lobby,
 * such as login authentication, listing lobbies, creating, or joining a lobby.
 */
public interface PreLobbyClientPayload extends ClientPayload {
}
