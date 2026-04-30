package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.shared.payload.client.*;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote server-side session contract exposed to one authenticated RMI client.
 * It mirrors the operations handled by server.virtualview.VirtualView for TCP,
 * but it must use void remote calls because responses are pushed through the
 * client's {@link RmiClientService} callback.
 *
 * Keep this contract aligned with VirtualView when commands change, while
 * preserving RMI-specific signatures: extends Remote and throws RemoteException.
 */
public interface RmiServerService extends Remote {

    void listLobbies(ListLobbiesPayload payload) throws RemoteException;
    void createLobby(CreateLobbyPayload payload) throws RemoteException;
    void joinLobby(JoinLobbyPayload payload) throws RemoteException;
    void exitLobby(ExitLobbyPayload payload) throws RemoteException;
    void startLobby(StartLobbyPayload payload) throws RemoteException;

    // IN-MATCH
    void moveTotem(MoveTotemPayload payload) throws RemoteException;
    void chooseCard(ChooseCardPayload payload) throws RemoteException;
    void extraDraw(ExtraDrawPayload payload) throws RemoteException;
    void pong() throws RemoteException;

    void closeSession() throws RemoteException;
}
