package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerRMI;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.client.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
public interface RmiServerFacade extends Remote {
    // PRE-MATCH
    // ATTENZIONE QUI: Il client, quando si autentica, "regala" al server il suo ricevitore!
    ServerMessage authenticate(AuthenticationPayload payload, VirtualServerRMI clientCallback) throws RemoteException;

    ServerMessage listLobbies(ListLobbiesPayload payload) throws RemoteException;
    ServerMessage createLobby(CreateLobbyPayload payload) throws RemoteException;
    ServerMessage joinLobby(JoinLobbyPayload payload) throws RemoteException;
    ServerMessage exitLobby(ExitLobbyPayload payload) throws RemoteException;
    ServerMessage startLobby(StartLobbyPayload payload) throws RemoteException;

    // IN-MATCH
    ServerMessage setupMatch(SetupMatchPayload payload) throws RemoteException;
    ServerMessage moveTotem(MoveTotemPayload payload) throws RemoteException;
    ServerMessage chooseCard(ChooseCardPayload payload) throws RemoteException;
    ServerMessage extraDraw(ExtraDrawPayload payload) throws RemoteException;
    ServerMessage resolveEffects(ResolveEffectsPayload payload) throws RemoteException;
    ServerMessage resetBoard(ResetBoardPayload payload) throws RemoteException;
    ServerMessage finalScore(FinalScorePayload payload) throws RemoteException;

    // Metodo bonus per gestire la disconnessione pulita (il close() del client)
    void disconnect(String nickname) throws RemoteException;
}
