package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.ClientEndpoint;

import java.io.IOException;

// Here we expose the services that the server can request to the client.
// This interface defines the common callback contract shared by TCP and RMI: with TCP
// these operations are reached through server-message deserialization and dispatch,
// while with RMI the same methods will be invoked directly as remote callbacks.
// On the server side, the concrete transport object acts as a proxy toward this contract.
public interface VirtualServer extends ClientEndpoint<RuntimeException>, AutoCloseable {
}
