package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

/**
 * Marker interface for client-to-preLobby payloads that manage connection-level details.
 * <p>
 * This interface is distinct from general application-level command payloads
 * and is primarily used for connection handshake and keep-alive ping-pong.
 */
public interface ConnectionClientPayload extends ClientPayload {
}
