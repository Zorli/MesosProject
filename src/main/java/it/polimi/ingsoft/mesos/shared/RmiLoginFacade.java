package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RmiLoginFacade extends Remote {
    RmiServerFacade authenticate(AuthenticationPayload payload, RmiClientFacade clientCallback) throws RemoteException;
}
