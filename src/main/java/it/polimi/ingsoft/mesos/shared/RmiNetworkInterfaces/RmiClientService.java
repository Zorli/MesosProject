package it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote callback exposed by the client and called by the preLobby in the RMI transport.
 * <p>
 * The RMI client has two directions:
 * <ul>
 *     <li>client to preLobby: {@link RmiServerService}, used by the client to send commands;</li>
 *     <li>preLobby to client: {@code RmiClientService}, used by the preLobby to push updates back.</li>
 * </ul>
 * <p>
 * The concrete client-side implementation is {@code VirtualServerRMI}. The name is intentional in the
 * project architecture: from the client point of view it behaves like a local "virtual preLobby" that receives
 * messages coming from the real preLobby and routes them to the UI.
 * <p>
 * This interface is placed in the shared package because Java RMI requires both JVMs to know the same remote
 * type. The preLobby receives an implementation of this interface during connection setup and stores it inside
 * the per-client RMI session.
 */
public interface RmiClientService extends Remote {

    /**
     * Receives one application message from the preLobby.
     * <p>
     * Server-side code calls this method when it wants to notify the client about lobby updates, lobby lists,
     * game updates, or application errors. The client implementation routes the message to the local
     * {@code ClientView} through the client-side message router.
     *
     * @param message preLobby-to-client message containing a type and its payload
     * @throws RemoteException if the callback cannot be delivered to the client JVM
     */
    void handleMessage(ServerMessage message) throws RemoteException;

    /**
     * Receives a keep-alive PING from the preLobby.
     * <p>
     * The client implementation answers by asking {@code ServerProxyRMI} to call
     * {@link RmiServerService#pong(it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload)}
     * on the remote preLobby session.
     *
     * @throws RemoteException if the callback cannot be delivered to the client JVM
     */
    void handlePing() throws RemoteException;
}
