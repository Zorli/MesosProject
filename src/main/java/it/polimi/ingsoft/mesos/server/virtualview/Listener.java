package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;

/**
 * Server-side contract for sending updates to one connected client.
 * <p>
 * This interface represents the outbound side of a virtual view, from server to
 * client. Models and controllers use it as an observer contract: they produce a
 * typed {@link ServerPayload} and notify the listener without knowing whether
 * the client is connected through TCP, RMI, or another transport.
 * <p>
 * Concrete virtual views translate the payload into their own network protocol.
 * For example, a TCP virtual view wraps it in a server message and serializes it
 * as JSON, while an RMI virtual view sends it through the remote client callback.
 */
public interface Listener {
    /**
     * Delivers one server update to the connected client represented by this
     * listener.
     *
     * @param payload typed server-to-client payload produced by model or
     *                controller logic
     */
    void notifyUpdate(ServerPayload payload);
}
