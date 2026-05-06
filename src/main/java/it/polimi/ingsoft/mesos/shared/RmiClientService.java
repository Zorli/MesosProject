package it.polimi.ingsoft.mesos.shared;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.rmi.Remote;
import java.rmi.RemoteException;
/**
 * Remote contract for the client-side callback mechanism over RMI.
 * This interface is exposed by the client and directly invoked by the server
 * to push asynchronous updates (e.g., game state changes, lobbies, errors).
 * It mirrors the callbacks handled by client.virtualserver.VirtualServer for TCP.
 * Unlike the pure VirtualServer interface, this contract enforces RMI constraints
 * (extending java.rmi.Remote and declaring RemoteExceptions).
 * It must reside in the shared module as the server needs it to hold client references.
 * Keep it aligned with VirtualServer when pushed server events change.
 */
public interface RmiClientService extends Remote{
    void handleError(ErrorPayload payload) throws RemoteException;
    void handlePreMatchUpdate(PreMatchUpdatePayload payload) throws RemoteException;
    void handleLobbyList(LobbyListPayload payload) throws RemoteException;
    void handleGameUpdate(GamePayload payload) throws RemoteException;
    void handleGlobalRanking(GlobalRankingPayload payload) throws RemoteException;
    void handleFinalMatchRanking(FinalMatchRankingPayload payload) throws RemoteException;
    void handleMatchHistory(MatchHistoryPayload payload) throws RemoteException;
    void handlePing() throws RemoteException;
}
