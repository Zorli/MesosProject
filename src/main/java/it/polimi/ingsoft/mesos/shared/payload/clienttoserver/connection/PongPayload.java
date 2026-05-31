package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection;

import java.io.Serializable;

/**
 * Keep-alive response payload sent by the client to confirm active connection status.
 * <p>
 * When the preLobby sends a keep-alive PING to the client, the client responds
 * with this payload to prevent connection timeout termination.
 */
public class PongPayload implements Serializable, ConnectionClientPayload {
}
