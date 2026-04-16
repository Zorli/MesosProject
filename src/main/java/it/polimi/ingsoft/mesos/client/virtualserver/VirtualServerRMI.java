package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiClientFacade;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * RMI callback receiver for the client.
 * <p>
 * This class acts as the inbound network boundary on the client side.
 * It is exported as a remote object and passed to the server during the authentication phase.
 * The server uses this remote reference to push asynchronous updates, game events,
 * and errors back to the client.
 * <p>
 * Upon receiving a remote invocation, this adapter translates and forwards the
 * network-level payload to the local {@link ClientView} (e.g., TUI or GUI) for rendering,
 * ensuring strict decoupling between the network transport and the user interface.
 */
public class VirtualServerRMI extends UnicastRemoteObject implements RmiClientFacade {

    private final ClientView clientView;

    public VirtualServerRMI(ClientView clientView) throws RemoteException {
        super();
        this.clientView = clientView;
    }

    @Override
    public void handleError(ErrorPayload payload) throws RemoteException {
        this.clientView.showError(payload);
    }

    @Override
    public void handlePreMatchUpdate(PreMatchUpdatePayload payload) throws RemoteException {
        this.clientView.showPreMatchUpdate(payload);
    }

    @Override
    public void handleLobbyList(LobbyListPayload payload) throws RemoteException {
        this.clientView.showLobbyList(payload);
    }

    @Override
    public void handleGameUpdate(GamePayload payload) throws RemoteException {
        this.clientView.showGameUpdate(payload);
    }
}