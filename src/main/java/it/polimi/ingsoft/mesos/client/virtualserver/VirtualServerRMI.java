package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.view.MessageRouter;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

// RMI implementation of the server -> client channel.
// It receives server callbacks and forwards them to the UI through ClientView.
public class VirtualServerRMI implements VirtualServer, RmiClientService {

    private final MessageRouter messageRouter;
    private final boolean exported;
    private ServerProxyRMI serverProxy;

    public VirtualServerRMI(ClientView clientView) throws RemoteException {
        this(clientView, true);
    }

    public VirtualServerRMI(ClientView clientView, boolean exported) throws RemoteException {
        if (exported) {
            UnicastRemoteObject.exportObject(this, 0);
        }
        this.messageRouter= new MessageRouter(clientView);
        this.exported = exported;
    }

    public void handleMessagge(ServerMessage message) {
        if(message.type.equals(ServerMessageType.PING)){
            this.handlePing();
            return;
        }
        this.messageRouter.routeMessage(message);
    }

    public void attachServerProxy(ServerProxyRMI serverProxy) {
        this.serverProxy = serverProxy;
    }

    @Override
    public void handlePing() {
        if (this.serverProxy == null) {
            throw new IllegalStateException("Cannot reply to ping before connection");
        }
        this.serverProxy.sendPong();
    }

    @Override
    public void close() throws NoSuchObjectException {
        if (this.exported) {
            UnicastRemoteObject.unexportObject(this, true);
        }
    }
}
