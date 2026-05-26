package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**
 * RMI entry point for incoming client connections.
 * <p>
 * This object is bound to the server's RMI Registry. Each client calls it once
 * to receive a dedicated {@link VirtualViewRMI} session.
 */
public class RmiConnectionEntryPoint extends UnicastRemoteObject implements RmiConnectionService {
    private final ServerController serverController;
    private final MessageRouter router;
    private final int rmiObjectPort;

    public RmiConnectionEntryPoint(ServerController serverController, MessageRouter router) throws RemoteException {
        this(serverController, router, 0);
    }

    public RmiConnectionEntryPoint(ServerController serverController, MessageRouter router, int rmiObjectPort) throws RemoteException {
        super(rmiObjectPort);
        this.serverController = serverController;
        this.router = router;
        this.rmiObjectPort = rmiObjectPort;
    }

    @Override
    public RmiServerService connect(RmiClientService clientCallback) throws RemoteException {
        return new VirtualViewRMI(this.serverController, clientCallback, this.router, this.rmiObjectPort, System.out);
    }
}
