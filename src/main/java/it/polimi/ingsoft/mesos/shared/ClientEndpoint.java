package it.polimi.ingsoft.mesos.shared;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;

// This contract forces TCP and RMI client endpoints to expose the same services.
// A single interface was not enough because the RMI version must throw RemoteException,
// while the TCP VirtualServer can keep using unchecked exceptions.
// ClientEndpoint represents the services offered by the client to the server:
// RmiClientService must stay in shared for RMI and VirtualServer is the local TCP-side contract.
// Their concrete implementations, VirtualServerRMI and VirtualServerTCP, are therefore forced to use the same API.
public interface ClientEndpoint<E extends Exception> {
    void handleMessage(ServerMessage message) throws E;
    void handlePing() throws E;
}
