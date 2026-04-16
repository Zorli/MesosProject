package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerRMI;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.client.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
public interface RmiServerFacade extends Remote {

    void listLobbies(ListLobbiesPayload payload) throws RemoteException;
    void createLobby(CreateLobbyPayload payload) throws RemoteException;
    void joinLobby(JoinLobbyPayload payload) throws RemoteException;
    void exitLobby(ExitLobbyPayload payload) throws RemoteException;
    void startLobby(StartLobbyPayload payload) throws RemoteException;

    // IN-MATCH
    void setupMatch(SetupMatchPayload payload) throws RemoteException;
    void moveTotem(MoveTotemPayload payload) throws RemoteException;
    void chooseCard(ChooseCardPayload payload) throws RemoteException;
    void extraDraw(ExtraDrawPayload payload) throws RemoteException;
    void resolveEffects(ResolveEffectsPayload payload) throws RemoteException;
    void resetBoard(ResetBoardPayload payload) throws RemoteException;
    void finalScore(FinalScorePayload payload) throws RemoteException;

    void closeSession() throws RemoteException;
}
