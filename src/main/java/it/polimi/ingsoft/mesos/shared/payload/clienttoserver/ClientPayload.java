package it.polimi.ingsoft.mesos.shared.payload.clienttoserver;

import java.io.Serializable;

/**
 * Marker interface for all data payloads sent from the client to the server.
 * <p>
 * Implementing classes represent arguments for various client actions, such as
 * login credentials, lobby management commands, or in-game player decisions.
 */
public interface ClientPayload extends Serializable {
}
