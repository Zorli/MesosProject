package it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;

/**
 * Marker interface for all preLobby-to-client payloads related to the lobby room phase.
 * <p>
 * Implementing classes contain data for updates occurring within a lobby,
 * such as player join/leave notifications or game-start alerts.
 */
public interface LobbyServerPayload extends ServerPayload {
}
