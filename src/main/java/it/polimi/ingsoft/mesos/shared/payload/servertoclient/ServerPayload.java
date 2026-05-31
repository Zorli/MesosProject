package it.polimi.ingsoft.mesos.shared.payload.servertoclient;

import java.io.Serializable;

/**
 * Marker interface for all data payloads sent from the server to the client.
 * <p>
 * Implementing classes represent status reports, list queries, error details,
 * or full game state snapshots broadcasted by the server.
 */
public interface ServerPayload extends Serializable {
}
