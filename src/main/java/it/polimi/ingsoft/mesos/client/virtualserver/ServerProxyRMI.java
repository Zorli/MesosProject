package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.RmiServerFacade;
import it.polimi.ingsoft.mesos.shared.payload.client.*;

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
    private final RmiServerFacade serverStub;
    private final VirtualServerRMI callbackReceiver;
    private volatile boolean connected;

    public ServerProxyRMI(RmiServerFacade serverStub, VirtualServer localViewUpdater) throws RemoteException {
        this.serverStub = serverStub;
        this.callbackReceiver = new VirtualServerRMI(localViewUpdater);

        this.connected = true;
    }
    @Override
    public boolean isConnected() {
        return this.connected;
    }
    @Override
    public void authenticate(String nickname) {
        if (!this.connected) return;

        try {
            this.serverStub.authenticate(new AuthenticationPayload(nickname),this.callbackReceiver);
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void listLobbies() {
        if (!this.connected) return;

        try {
            this.serverStub.listLobbies(new ListLobbiesPayload());
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }

    }
    @Override
    public void createLobby(int chosenCapacity){
        if (!this.connected) return;
        try {
            this.serverStub.createLobby(new CreateLobbyPayload(chosenCapacity));
        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    @Override
    public void joinLobby(int lobbyId) {
        if (!this.connected) return;

        try {
            this.serverStub.joinLobby(new JoinLobbyPayload(lobbyId));

        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }
    @Override
    public void exitLobby(int lobbyId) {
        if (!this.connected) return;
        try {
            this.serverStub.exitLobby(new ExitLobbyPayload(lobbyId));

        } catch (RemoteException e) {
            this.handleDisconnection(e);
        }
    }

    // to continue on reference of RmiServerFacade
    // Metodo helper interno per gestire la caduta di rete
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
}
