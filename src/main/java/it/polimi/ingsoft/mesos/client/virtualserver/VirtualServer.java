package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;

import java.io.IOException;

/**
 * Client-side abstraction for the server-to-client communication path.
 * <p>
 * In the client architecture, {@code ServerProxy} and {@code VirtualServer} represent the two opposite
 * directions of the network connection:
 * <ul>
 *     <li>{@code ServerProxy}: client-to-server path, used by controllers to send user commands;</li>
 *     <li>{@code VirtualServer}: server-to-client path, used to receive server messages and forward them
 *     to the local client view.</li>
 * </ul>
 * <p>
 * The name "VirtualServer" means that, from the client point of view, this object behaves like a local
 * endpoint representing the real server. It does not contain game logic. Its responsibility is to receive
 * messages from the transport layer, translate transport-specific events when needed, and route the resulting
 * server messages toward the UI.
 * <p>
 * In the TCP transport, {@link VirtualServerTCP} implements this interface and runs in a background thread
 * that continuously reads JSON messages from the socket. In the RMI transport, {@link VirtualServerRMI}
 * plays the same architectural role through remote callbacks exposed to the server.
 * <p>
 * Implementations must keep this class of objects focused on networking and message dispatching. User
 * commands belong to {@code ServerProxy}; rendering and user interaction belong to {@code ClientView}.
 */
public interface VirtualServer extends AutoCloseable {

    /**
     * Handles one server-to-client application message.
     * <p>
     * Typical messages include lobby updates, lobby lists, game updates, and server-side errors. The
     * implementation should route the message to the client-side view layer, usually through the shared
     * client message router.
     *
     * @param message message received from the server
     */
    void handleMessage(ServerMessage message);

    /**
     * Handles a server keep-alive PING.
     * <p>
     * The expected behavior is to ask the matching {@code ServerProxy} to send a PONG back through the
     * client-to-server channel. This keeps the heartbeat protocol out of the UI and inside the networking
     * layer.
     */
    void handlePing();

    /**
     * Releases the transport resources owned by this receiver.
     * <p>
     * TCP implementations close the socket-side receiving path. RMI implementations unexport the callback
     * object when it was exported. The exact resource depends on the transport, but callers can use this
     * method as the common lifecycle operation for shutting down the receiver.
     *
     * @throws IOException if the underlying transport resource cannot be closed
     */
    void close() throws IOException;
}
