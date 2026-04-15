package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.IOException;
// Here we expose the services that the server can request to the client.
// This interface defines the common callback contract shared by TCP and RMI: with TCP
// these operations are reached through server-message deserialization and dispatch,
// while with RMI the same methods will be invoked directly as remote callbacks.
// On the server side, the concrete transport object acts as a proxy toward this contract.
public interface VirtualServer extends AutoCloseable {

    //void heartBeat(); TODO: Implement the Ping-Pong protocol here

    void handleError(ErrorPayload payload);

    void handlePreMatchUpdate(PreMatchUpdatePayload payload);

    void handleLobbyList(LobbyListPayload payload);

    void handleGameUpdate(GamePayload payload);

    @Override
    void close() throws IOException;
}
