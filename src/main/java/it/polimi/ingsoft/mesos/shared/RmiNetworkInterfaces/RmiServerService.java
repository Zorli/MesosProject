package it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI server-side session exposed to one connected client.
 * It mirrors the commands accepted by the TCP VirtualView, but RMI calls are void
 * because every server response is pushed through the client callback.
 * It is shared to keep client and server on the same remote contract.
 */
public interface RmiServerService extends Remote {
    void sendMessage(ClientPayload payload) throws RemoteException;
    void pong(PongPayload payload) throws RemoteException;
    void closeSession() throws RemoteException;
}
