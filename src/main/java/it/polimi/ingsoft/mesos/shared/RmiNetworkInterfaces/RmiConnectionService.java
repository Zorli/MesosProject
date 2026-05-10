package it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI entry point exposed in the registry.
 * It mirrors the first connection step of the TCP server: the client connects and
 * receives a server-side session, while passing the callback used for server pushes.
 * It is shared because both sides must load the same remote interfaces.
 */
public interface RmiConnectionService extends Remote {
    RmiServerService connect(RmiClientService clientCallback) throws RemoteException;
}
