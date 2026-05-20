package it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;

/**
 * Marker interface for all server-to-client payloads related to the pre-lobby phase.
 * <p>
 * Implementing classes represent status updates and responses sent from the server
 * to clients who are not yet inside a lobby room (e.g. login result, list of lobbies).
 */
public interface PreLobbyServerPayload extends ServerPayload {
}
