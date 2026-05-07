package it.polimi.ingsoft.mesos.shared;

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
public interface RmiServerService extends Remote, ServerEndpoint<RemoteException> {}
