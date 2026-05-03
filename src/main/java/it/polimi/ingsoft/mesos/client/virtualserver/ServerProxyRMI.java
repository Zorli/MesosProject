package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.*;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**
 * RMI implementation of the ServerProxy contract.
 * This class acts as the outbound network boundary for the client. It intercepts
 * local, transport-agnostic method calls from the GUI/TUI controllers, wraps the
 * parameters into structured Payloads, and dispatches them over the network using
 * the RMI Server Stub.
 * <p>
 * It also manages the lifecycle of the local RMI callback receiver, ensuring it
 * is correctly exported upon connection and safely unexported upon closure to
 * prevent background thread leaks.
 */
public class ServerProxyRMI implements ServerProxy{
    private final RmiConnectionService loginFacade;
    private RmiServerService serverStub;
    private final VirtualServerRMI callbackReceiver;
    private volatile boolean connected;

    public ServerProxyRMI(RmiConnectionService loginFacade, ClientView clientView) throws RemoteException {
        this.loginFacade = loginFacade;
        this.callbackReceiver = new VirtualServerRMI(clientView);

        this.connected = true;
    }

    //Servizi offerti in uscita
    @Override
    public boolean isConnected() {
        return this.connected;
    }
    @Override
    public void authenticate(String nickname) {
        if (!this.connected) {
            this.notifyLocalError("AUTH_ERROR", "client already Authenticated");
            return;
        }
        if (this.serverStub != null) { //If the client request authentication,and it's already in,  we return an error
            try {
                this.callbackReceiver.handleError(new ErrorPayload("AUTH_ERROR", "client already Authenticated"));
            } catch (RemoteException ignored) {
            }
            return;
        }
        try {
            this.serverStub = this.loginFacade.authenticate(new AuthenticationPayload(nickname), this.callbackReceiver);
            this.callbackReceiver.attachServerStub(this.serverStub);

        } catch (RemoteException e) {
            if(this.serverStub == null) {
            } else {
                this.handleDisconnection(e);
            }
        }
    }

    @Override
    public void listLobbies() {
        if (!this.ensureAuthenticated()) return;

        try {
            this.serverStub.listLobbies(new ListLobbiesPayload());
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }

    }
    @Override
    public void createLobby(int chosenCapacity){
        if (!this.ensureAuthenticated()) return;
        try {
            this.serverStub.createLobby(new CreateLobbyPayload(chosenCapacity));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void joinLobby(int lobbyId) {
        if (!this.ensureAuthenticated()) return;

        try {
            this.serverStub.joinLobby(new JoinLobbyPayload(lobbyId));

        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }
    @Override
    public void exitLobby() {
        if (!this.ensureAuthenticated()) return;
        try {
            this.serverStub.exitLobby(new ExitLobbyPayload());

        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void startLobby() {
        if (!this.ensureAuthenticated()) return;
        try {
            this.serverStub.startLobby(new StartLobbyPayload());
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void moveTotem(String tile) {
        if (!this.ensureAuthenticated()) return;
        try {
            this.serverStub.moveTotem(new MoveTotemPayload(tile));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void chooseCard(int cardId) {
        if (!this.ensureAuthenticated()) return;
        try {
            this.serverStub.chooseCard(new ChooseCardPayload(cardId));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void extraDraw(int cardId) {
        if (!this.ensureAuthenticated()) return;
        try {
            this.serverStub.extraDraw(new ExtraDrawPayload(cardId));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    // to continue on reference of RmiServerFacade
    private void handleDisconnection(RemoteException e) {
        this.connected = false;
        System.err.println("Connessione RMI persa con il server!");

        // (Opzionale ma molto utile): Avvisare la GUI locale che la connessione è morta
        // myCallbackReceiver.receiveError(new ErrorPayload("DISCONNECTED", "Connessione persa"));
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
            UnicastRemoteObject.unexportObject(this.callbackReceiver, true);
        }
    }
    private void notifyLocalError(String code, String message) {
        try {
            this.callbackReceiver.handleError(new ErrorPayload(code, message));
        } catch (RemoteException ignored) {
        }
    }

    private boolean ensureAuthenticated() {
        if (!this.connected) {
            this.notifyLocalError("AUTH_ERROR", "Client is not connected");
            return false;
        }
        if (this.serverStub == null) {
            this.notifyLocalError("AUTH_ERROR", "Client is not authenticated");
            return false;
        }
        return true;
    }
}
