package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**
 * RMI entry point and session factory for incoming client connections.
 * <p>
 * This singleton object is bound to the server's RMI Registry and acts as the public
 * "Gatekeeper". It exposes the initial authentication endpoint for all connecting clients.
 * <p>
 * When a client successfully authenticates via this facade, the factory instantiates
 * and exports a dedicated, stateful {@link VirtualViewRMI} session exclusively for that user.
 * This "Session Factory" pattern ensures that unauthenticated clients cannot access
 * core game logic and that each connected client maintains a strictly isolated connection context.
 */
public class RmiSessionFactory extends UnicastRemoteObject implements RmiConnectionService {
    private final ServerController serverController;
    private final MessageRouter router;

    public RmiSessionFactory(ServerController serverController, MessageRouter router) throws RemoteException {
        super();
        this.serverController = serverController;
        this.router = router;
    }

    @Override
    public RmiServerService connect(RmiClientService clientCallback) throws RemoteException {
        return new VirtualViewRMI(this.serverController, clientCallback, this.router);
    }
}
