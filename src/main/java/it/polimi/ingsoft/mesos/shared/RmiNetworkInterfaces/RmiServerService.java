package it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces;

import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote server-side session exposed to one connected RMI client.
 * <p>
 * The client receives this object from {@link RmiConnectionService#connect(RmiClientService)} and stores it
 * inside {@code ServerProxyRMI}. From that point on, every client-to-server command goes through this
 * per-client session.
 * <p>
 * The concrete server-side implementation is {@code VirtualViewRMI}. It adapts RMI calls to the same controller
 * and message-router flow used by the TCP transport. In other words, TCP and RMI differ only in
 * transport mechanics: once a {@link ClientMessage} reaches the server-side virtual view, it is routed through
 * the normal server application pipeline.
 * <p>
 * All methods return {@code void} because server responses are not returned directly from the RMI call. The
 * server answers asynchronously by calling the {@link RmiClientService} callback that was provided during
 * connection setup.
 */
public interface RmiServerService extends Remote {

    /**
     * Sends one application command message from the client to the server.
     * <p>
     * The message contains the protocol type and the client-to-server payload. The server-side implementation
     * uses the session associated with this remote object and routes the message through the normal server
     * pipeline.
     *
     * @param clientMessage client-to-server command message
     * @throws RemoteException if the remote session cannot receive or process the call at the RMI level
     */
    void handleClientMessage(ClientMessage clientMessage) throws RemoteException;

    /**
     * Sends a keep-alive PONG reply from the client to the server.
     * <p>
     * This method is separate from {@link #handleClientMessage(ClientMessage)} because the ping-pong protocol is
     * connection-level infrastructure, not a user command. The server uses it to record that the client is
     * still alive.
     *
     * @param payload keep-alive reply payload
     * @throws RemoteException if the remote session cannot receive the PONG
     */
    void pong(PongPayload payload) throws RemoteException;

    /**
     * Closes the server-side session associated with this client.
     * <p>
     * The client calls this during normal shutdown. The server-side implementation disconnects the session
     * from the controller and unexports the remote session object so that it is no longer available through RMI.
     *
     * @throws RemoteException if the remote close call cannot be delivered
     */
    void closeSession() throws RemoteException;
}
