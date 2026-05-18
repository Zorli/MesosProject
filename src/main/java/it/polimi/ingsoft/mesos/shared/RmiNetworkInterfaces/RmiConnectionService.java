package it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote entry point published by the server in the RMI registry.
 * <p>
 * This interface represents only the first step of an RMI client connection. The client looks up an
 * implementation of this service from the registry, passes its own {@link RmiClientService} callback, and
 * receives a dedicated {@link RmiServerService} session in return.
 * <p>
 * The concrete server-side implementation is {@code RmiConnectionEntryPoint}. It creates one {@code VirtualViewRMI}
 * per connected client. This keeps the registry object stateless and gives each client its own remote session,
 * session id, and callback reference.
 * <p>
 * This split mirrors the TCP architecture:
 * <ul>
 *     <li>TCP accepts a socket and creates one virtual view for that client;</li>
 *     <li>RMI accepts a callback and returns one remote session for that client.</li>
 * </ul>
 */
public interface RmiConnectionService extends Remote {

    /**
     * Creates a new server-side RMI session for one client.
     * <p>
     * The {@code clientCallback} parameter is the remote object exported by the client. The server stores it
     * in the new session and uses it later to send server-to-client messages and keep-alive pings.
     *
     * @param clientCallback client-side callback used by the server to push messages back to this client
     * @return dedicated remote server session used by the client to send commands after connection setup
     * @throws RemoteException if the session cannot be created or the remote call fails
     */
    RmiServerService connect(RmiClientService clientCallback) throws RemoteException;
}
