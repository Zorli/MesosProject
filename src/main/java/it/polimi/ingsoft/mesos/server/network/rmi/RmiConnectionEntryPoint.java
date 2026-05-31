package it.polimi.ingsoft.mesos.server.network.rmi;

import it.polimi.ingsoft.mesos.server.helper.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ServerConnectionLifecycle;
import it.polimi.ingsoft.mesos.server.network.tcp.VirtualViewTCP;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**
 * RMI entry point published by the preLobby for incoming RMI clients.
 * <p>
 * This object is bound in the preLobby RMI registry and represents only the first
 * step of the RMI connection protocol. A client looks it up, passes its own
 * {@link RmiClientService} callback, and receives a dedicated
 * {@link RmiServerService} session object in return.
 * <p>
 * Architecturally, this is the RMI counterpart of the TCP acceptor. The TCP
 * acceptor receives a socket and creates one {@link VirtualViewTCP};
 * this entry point receives a client callback and creates one
 * {@link VirtualViewRMI}. In both cases, the created virtual view becomes the
 * per-client network boundary used by the rest of the preLobby.
 * <p>
 * This class does not authenticate the player and does not route application
 * commands. Authentication is a later client command handled through the
 * returned virtual view. Routing is delegated to {@link MessageRouter} after the
 * virtual view receives client payloads.
 */
public class RmiConnectionEntryPoint extends UnicastRemoteObject implements RmiConnectionService {
    private final ServerConnectionLifecycle serverConnectionLifecycle;
    private final MessageRouter router;

    /**
     * Creates the remote RMI entry point.
     *
     * @param serverConnectionLifecycle manager shared by the per-client RMI virtual
     *                         views for session lifecycle operations
     * @param router router shared by the per-client RMI virtual views for
     *               client payload dispatch
     * @throws RemoteException if the remote object cannot be exported
     */
    public RmiConnectionEntryPoint(ServerConnectionLifecycle serverConnectionLifecycle, MessageRouter router) throws RemoteException {
        super();
        this.serverConnectionLifecycle = serverConnectionLifecycle;
        this.router = router;
    }

    /**
     * Creates a dedicated RMI preLobby session for one client.
     * <p>
     * The client callback is the remote object that the preLobby will later use to
     * push messages and keep-alive pings back to that client. The returned
     * {@link VirtualViewRMI} stores that callback, registers itself as a preLobby
     * connection, and becomes the object through which the client sends all later
     * commands.
     *
     * @param clientCallback remote callback exported by the connecting client
     * @return dedicated RMI preLobby-side session for that client
     * @throws RemoteException if the remote session cannot be created or returned
     */
    @Override
    public RmiServerService connect(RmiClientService clientCallback) throws RemoteException {
        return new VirtualViewRMI(this.serverConnectionLifecycle, clientCallback, this.router);
    }
}
