package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.*;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

import java.io.IOException;
import java.rmi.NoSuchObjectException;
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
    @Override
    public boolean isConnected() {
        return this.connected;
    }
    @Override
    public boolean authenticate(String nickname) {
        if (!this.connected) {
            return false;
        }
        try {
            System.out.println("[RMI] Authenticating with nickname: " + nickname);
            this.serverStub = this.loginFacade.authenticate(new AuthenticationPayload(nickname), this.callbackReceiver);
            return true;
        } catch (RemoteException e) {
            System.err.println("[RMI] Authentication failed: " + e.getMessage());
            this.handleDisconnection(e);
            return false;
        }
    }

    @Override
    public boolean listLobbies() {
        if (!this.connected) {
            return false;
        }

        try {
            System.out.println("[RMI DEBUG] ServerProxyRMI.listLobbies() - serverStub is null: " + (this.serverStub == null));
            if (this.serverStub != null) {
                System.out.println("[RMI DEBUG] Calling serverStub.listLobbies()");
                this.serverStub.listLobbies(new ListLobbiesPayload());
            } else {
                System.out.println("[RMI DEBUG] Calling loginFacade.listLobbies()");
                this.loginFacade.listLobbies(new ListLobbiesPayload(), this.callbackReceiver);
            }
            return true;
        } catch (RemoteException e) {
            System.err.println("[RMI DEBUG] RemoteException in listLobbies: " + e.getMessage());
            e.printStackTrace();
            this.handleDisconnection(e);
            return false;
        }

    }
    @Override
    public boolean createLobby(int chosenCapacity){
        if (!this.connected) {
            return false;
        }
        try {
            if (this.serverStub != null) {
                this.serverStub.createLobby(new CreateLobbyPayload(chosenCapacity));
            } else {
                this.loginFacade.createLobby(new CreateLobbyPayload(chosenCapacity), this.callbackReceiver);
            }
            return true;
        } catch (RemoteException e) {
            this.handleDisconnection(e);
            return false;
        }
    }

    @Override
    public boolean joinLobby(int lobbyId) {
        if (!this.connected) {
            return false;
        }

        try {
            if (this.serverStub != null) {
                this.serverStub.joinLobby(new JoinLobbyPayload(lobbyId));
            } else {
                this.loginFacade.joinLobby(new JoinLobbyPayload(lobbyId), this.callbackReceiver);
            }
            return true;
        } catch (RemoteException e) {
            this.handleDisconnection(e);
            return false;
        }
    }
    @Override
    public boolean exitLobby(int lobbyId) {
        if (!this.connected) {
            return false;
        }
        try {
            if (this.serverStub != null) {
                this.serverStub.exitLobby(new ExitLobbyPayload(lobbyId));
            } else {
                this.loginFacade.exitLobby(new ExitLobbyPayload(lobbyId), this.callbackReceiver);
            }
            return true;
        } catch (RemoteException e) {
            this.handleDisconnection(e);
            return false;
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
            UnicastRemoteObject.unexportObject(this.callbackReceiver, true);
        } catch (NoSuchObjectException ignored) {
        }
    }
    private void notifyLocalError(String code, String message) {
        try {
            this.callbackReceiver.handleError(new ErrorPayload(code, message));
        } catch (RemoteException ignored) {
        }
    }
}
