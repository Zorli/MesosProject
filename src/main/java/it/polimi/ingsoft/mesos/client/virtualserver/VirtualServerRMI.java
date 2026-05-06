package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyRMI;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

// RMI implementation of the server -> client channel.
// It receives server callbacks and forwards them to the UI through ClientView.
public class VirtualServerRMI implements VirtualServer, RmiClientService {

    private final ClientView clientView;
    private final boolean exported;
    private ServerProxyRMI serverProxy;

    public VirtualServerRMI(ClientView clientView) throws RemoteException {
        this(clientView, true);
    }

    public VirtualServerRMI(ClientView clientView, boolean exported) throws RemoteException {
        if (exported) {
            UnicastRemoteObject.exportObject(this, 0);
        }
        this.clientView = clientView;
        this.exported = exported;
    }

    public void attachServerProxy(ServerProxyRMI serverProxy) {
        this.serverProxy = serverProxy;
    }

    @Override
    public void handleError(ErrorPayload payload) {
        this.clientView.showError(payload);
    }

    @Override
    public void handlePreMatchUpdate(PreMatchUpdatePayload payload) {
        this.clientView.showPreMatchUpdate(payload);
    }

    @Override
    public void handleLobbyList(LobbyListPayload payload) {
        this.clientView.showLobbyList(payload);
    }

    @Override
    public void handleGameUpdate(GamePayload payload) {
        this.clientView.showGameUpdate(payload);
    }

    @Override
    public void handleGlobalRanking(GlobalRankingPayload payload) {
        this.clientView.showGlobalRanking(payload);
    }

    @Override
    public void handleFinalMatchRanking(FinalMatchRankingPayload payload) {
        this.clientView.showFinalMatchRanking(payload);
    }

    @Override
    public void handleMatchHistory(MatchHistoryPayload payload) {
        this.clientView.showMatchHistory(payload);
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
