package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.shared.payload.client.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
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

    void closeSession() throws RemoteException;
}
