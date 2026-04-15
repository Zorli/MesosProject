package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.RmiClientFacade;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**
 * RMI Adapter for the VirtualServer contract.
 * This class acts as a network boundary and bridge between the RMI transport layer
 * and the local client logic.
 * * By extending UnicastRemoteObject, it acts as an RMI skeleton, receiving remote
 * procedure calls directly from the server. It then unpacks and delegates these
 * calls to the pure, transport-agnostic VirtualServer interface, ensuring that
 * the local GUI/TUI does not depend on RMI libraries or exceptions.
 */
public class VirtualServerRMI extends UnicastRemoteObject implements RmiClientFacade {
    private final VirtualServer localViewUpdater;

    public VirtualServerRMI(VirtualServer localViewUpdater) throws RemoteException {
        super();
        this.localViewUpdater = localViewUpdater;
    }
    @Override
    public void handleError(ErrorPayload payload) throws RemoteException{
        this.localViewUpdater.handleError(payload);
    }
    @Override
    public void handleGameUpdate(GamePayload payload) throws RemoteException {
        this.localViewUpdater.handleGameUpdate(payload);
    }
    @Override
    public void handlePreMatchUpdate(PreMatchUpdatePayload payload)  throws RemoteException {
        this.localViewUpdater.handlePreMatchUpdate(payload);
    }
    @Override
    public void handleLobbyList(LobbyListPayload payload) throws RemoteException {
        this.localViewUpdater.handleLobbyList(payload);
    }

}
