package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualViewRMI;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**
 * RMI entry point published by the server for incoming RMI clients.
 * <p>
 * This object is bound in the server RMI registry and represents only the first
 * step of the RMI connection protocol. A client looks it up, passes its own
 * {@link RmiClientService} callback, and receives a dedicated
 * {@link RmiServerService} session object in return.
 * <p>
 * Architecturally, this is the RMI counterpart of the TCP acceptor. The TCP
 * acceptor receives a socket and creates one {@link it.polimi.ingsoft.mesos.server.virtualview.VirtualViewTCP};
 * this entry point receives a client callback and creates one
 * {@link VirtualViewRMI}. In both cases, the created virtual view becomes the
 * per-client network boundary used by the rest of the server.
 * <p>
 * This class does not authenticate the player and does not route application
 * commands. Authentication is a later client command handled through the
 * returned virtual view. Routing is delegated to {@link MessageRouter} after the
 * virtual view receives client payloads.
 */
public class RmiConnectionEntryPoint extends UnicastRemoteObject implements RmiConnectionService {
    private final ServerController serverController;
    private final MessageRouter router;

    /**
     * Creates the remote RMI entry point.
     *
     * @param serverController controller shared by the per-client RMI virtual
     *                         views for session lifecycle operations
     * @param router router shared by the per-client RMI virtual views for
     *               client payload dispatch
     * @throws RemoteException if the remote object cannot be exported
     */
    public RmiConnectionEntryPoint(ServerController serverController, MessageRouter router) throws RemoteException {
        super();
        this.serverController = serverController;
        this.router = router;
    }

    /**
     * Creates a dedicated RMI server session for one client.
     * <p>
     * The client callback is the remote object that the server will later use to
     * push messages and keep-alive pings back to that client. The returned
     * {@link VirtualViewRMI} stores that callback, registers itself as a server
     * connection, and becomes the object through which the client sends all later
     * commands.
     *
     * @param clientCallback remote callback exported by the connecting client
     * @return dedicated RMI server-side session for that client
     * @throws RemoteException if the remote session cannot be created or returned
     */
    @Override
    public RmiServerService connect(RmiClientService clientCallback) throws RemoteException {
        return new VirtualViewRMI(this.serverController, clientCallback, this.router);
    }
}
