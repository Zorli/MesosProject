package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.shared.payload.client.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;

// This contract forces TCP and RMI server endpoints to expose the same services.
// A single interface was not enough because the RMI version must throw RemoteException,
// while the TCP VirtualView can keep using unchecked exceptions.
// ServerEndpoint represents the services offered by the server to the client:
// RmiServerService must stay in shared for RMI and VirtualView is the local TCP-side contract.
// Their concrete implementations, VirtualViewRMI and VirtualViewTCP, are therefore forced to use the same API.
public interface ServerEndpoint<E extends Exception> {
    void sendMessage(ClientPayload payload) throws E;
    void pong(PongPayload payload) throws E;
    void closeSession() throws E;
}
