package it.polimi.ingsoft.mesos.server.network.interfaces;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;

/**
 * Server-side contract for sending updates to one connected client.
 * <p>
 * This interface represents the outbound side of a virtual view, from preLobby to
 * client. Models and controllers use it as an observer contract: they produce a
 * typed {@link ServerPayload} and notify the listener without knowing whether
 * the client is connected through TCP, RMI, or another transport.
 * <p>
 * Concrete virtual views translate the payload into their own network protocol.
 * For example, a TCP virtual view wraps it in a preLobby message and serializes it
 * as JSON, while an RMI virtual view sends it through the remote client callback.
 */
public interface Listener {
    /**
     * Delivers one preLobby update to the connected client represented by this
     * listener.
     *
     * @param payload typed preLobby-to-client payload produced by model or
     *                controller logic
     */
    void notifyUpdate(ServerPayload payload);
}
