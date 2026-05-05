package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.client.*;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;

// Here we expose the services that the client can request to the server.
// This interface defines the common contract shared by TCP and RMI: with TCP these
// operations are reached through message deserialization and dispatch, while with RMI
// the same methods will be invoked directly as remote calls.
// On the client side, the concrete transport object acts as a proxy toward this contract.
public interface VirtualView extends PingPongConnection {
    // Pre-match
    void sendMessage(ClientPayload payload);

    void sendPing();
    void recordPong();
    String describeSession();
}
