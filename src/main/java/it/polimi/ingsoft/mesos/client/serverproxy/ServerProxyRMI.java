package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerRMI;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListLobbiesPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ApplicationErrorCode;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

import java.io.IOException;
import java.rmi.RemoteException;

// RMI implementation of the client -> server channel.
// The UI controllers use only the ServerProxy contract, without knowing if the network is TCP or RMI.
// This class owns the RMI server stub: every outbound client message, including PONG replies,
// must pass through here.
public class ServerProxyRMI implements ServerProxy {
    private RmiServerService serverStub;
    private final VirtualServerRMI callbackReceiver;
    private boolean connected;

    public ServerProxyRMI(RmiConnectionService connectionService, ClientView clientView) throws RemoteException {
        this(connectionService, clientView, true);
    }

    ServerProxyRMI(RmiConnectionService connectionService, ClientView clientView, boolean exportCallback) throws RemoteException {
        this.callbackReceiver = new VirtualServerRMI(clientView, exportCallback);
        this.serverStub = connectionService.connect(this.callbackReceiver);
        this.callbackReceiver.attachServerProxy(this);
        this.connected = true;
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public void authenticate(String nickname) {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.authenticate(new AuthenticationPayload(nickname));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void listLobbies() {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.listLobbies(new ListLobbiesPayload());
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }

    }

    @Override
    public void createLobby(int chosenCapacity){
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.createLobby(new CreateLobbyPayload(chosenCapacity));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void joinLobby(int lobbyId) {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.joinLobby(new JoinLobbyPayload(lobbyId));

        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void exitLobby() {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.exitLobby(new ExitLobbyPayload());

        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void startLobby() {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.startLobby(new StartLobbyPayload());
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void moveTotem(String tile) {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.moveTotem(new MoveTotemPayload(tile));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void chooseCard(int cardId) {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.chooseCard(new ChooseCardPayload(cardId));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void extraDraw(int cardId) {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.extraDraw(new ExtraDrawPayload(cardId));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    public void sendPong() {
        if (!this.ensureConnected()) return;
        try {
            this.serverStub.pong(new PongPayload());
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    private void handleDisconnection(RemoteException e) {
        this.connected = false;
        this.notifyLocalError(ApplicationErrorCode.CONNECTION_CLOSED, e.getMessage());
    }

    @Override
    public void close() throws IOException {
        this.connected = false;
        try {
            if (this.serverStub != null) {
                this.serverStub.closeSession();
                this.serverStub = null;
            }
        } finally {
            this.callbackReceiver.close();
        }
    }

    private void notifyLocalError(String code, String message) {
        this.callbackReceiver.handleError(new ErrorPayload(code, message));
    }

    private void notifyLocalError(ApplicationErrorCode code, String message) {
        this.notifyLocalError(code.name(), message);
    }

    private boolean ensureConnected() {
        if (this.connected) {
            return true;
        }
        this.notifyLocalError(ApplicationErrorCode.CONNECTION_CLOSED, "Client is not connected");
        return false;
    }
}
