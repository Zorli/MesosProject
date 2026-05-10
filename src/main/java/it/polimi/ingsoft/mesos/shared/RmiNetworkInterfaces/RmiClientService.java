package it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI client callback exposed by the client and used by the server to push messages.
 * It mirrors the server-to-client path used by the TCP VirtualServer, but with the
 * Remote/RemoteException contract required by RMI.
 * It is shared because both client and server must know the same remote type.
 */
public interface RmiClientService extends Remote {
    void handleMessage(ServerMessage message) throws RemoteException;
    void handlePing() throws RemoteException;
}
