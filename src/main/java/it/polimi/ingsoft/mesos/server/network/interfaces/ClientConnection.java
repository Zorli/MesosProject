package it.polimi.ingsoft.mesos.server.network.interfaces;

import java.io.Closeable;

/**
 * Minimal server-side contract for a registered client connection.
 * <p>
 * This interface is used by the connection lifecycle and keep-alive code without
 * depending on the concrete transport. A TCP virtual view and an RMI virtual
 * view expose the same operations here even though one writes to a socket and
 * the other calls a remote callback.
 * <p>
 * The contract covers connection management only: sending ping messages and
 * closing the connection. Receiving server updates is a separate responsibility
 * represented by {@link Listener}; a concrete class may implement both
 * interfaces, but they model different directions of communication.
 */
public interface ClientConnection extends Closeable {
    /**
     * Sends a keep-alive ping to the remote client using the concrete transport.
     * <p>
     * The ping-pong routine calls this method periodically for sessions that are
     * still considered active. The corresponding pong is later recorded through
     * the virtual view and server lifecycle.
     */
    void sendPing();
}
