package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.shared.payload.client.*;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RmiConnectionService extends Remote {
    RmiServerService authenticate(AuthenticationPayload payload, RmiClientService clientCallback) throws RemoteException;
    
    void listLobbies(ListLobbiesPayload payload, RmiClientService clientCallback) throws RemoteException;
    
    void createLobby(CreateLobbyPayload payload, RmiClientService clientCallback) throws RemoteException;
    
    void joinLobby(JoinLobbyPayload payload, RmiClientService clientCallback) throws RemoteException;
    
    void exitLobby(ExitLobbyPayload payload, RmiClientService clientCallback) throws RemoteException;
}
