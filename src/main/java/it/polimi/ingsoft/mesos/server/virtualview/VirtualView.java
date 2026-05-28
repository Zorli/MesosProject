package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;

/**
 * Server-side contract for a virtual view that receives client commands.
 * <p>
 * A virtual view is the boundary object that represents one connected client
 * inside the server. It hides the concrete transport from the server logic: TCP,
 * RMI, or any other protocol can receive a client command in a different way,
 * but the server only needs a structured {@link ClientMessage}, a way to record
 * pong replies, and a way to close the session.
 * <p>
 * This interface describes the inbound side of a virtual view, from client to
 * server. The outbound side, from server to client, is represented separately by
 * {@link Listener}, which receives server payloads and sends them through the
 * concrete transport.
 */
public interface VirtualView {
    /**
     * Passes a structured client message from the transport layer to the server.
     * <p>
     * Implementations normally attach their session id and delegate the payload
     * to the routing layer, so the command reaches the correct pre-lobby, lobby,
     * or game controller.
     *
     * @param clientMessage client command already parsed from the transport
     *                      protocol
     */
    void handleClientMessage(ClientMessage clientMessage);

    /**
     * Handles a pong response received from the client.
     * <p>
     * The pong belongs to the connection keep-alive protocol, not to game logic.
     * Implementations use it to refresh the session activity timestamp through
     * the server connection lifecycle.
     *
     * @param payload pong payload received from the client message envelope
     */
    void pong(PongPayload payload);

    /**
     * Requests closure of the client session represented by this virtual view.
     * <p>
     * The interface does not prescribe how the concrete transport is closed.
     * Implementations are responsible for performing their own cleanup and for
     * triggering the server-side disconnection flow.
     */
    void closeSession();
}
