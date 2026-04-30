package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Public RMI entry point bound in the registry.
 * It is the remote equivalent of the initial connection/authentication step:
 * after authentication it returns the per-client {@link RmiServerService}.
 *
 * RMI contracts live in shared because both client and server must load the
 * exact same remote interfaces, and their signatures must declare RemoteException.
 */
public interface RmiConnectionService extends Remote {
    RmiServerService authenticate(AuthenticationPayload payload, RmiClientService clientCallback) throws RemoteException;
}
