package it.polimi.ingsoft.mesos.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Public RMI entry point bound in the registry.
 * It creates the per-client {@link RmiServerService} session and receives the
 * client callback used by the server to push updates.
 *
 * RMI contracts live in shared because both client and server must load the
 * exact same remote interfaces, and their signatures must declare RemoteException.
 */
public interface RmiConnectionService extends Remote {
    RmiServerService connect(RmiClientService clientCallback) throws RemoteException;
}
