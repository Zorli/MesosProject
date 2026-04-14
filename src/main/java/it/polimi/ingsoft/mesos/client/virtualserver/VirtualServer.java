package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.IOException;
//Inteface to be implemented both by tcp and RMI
public interface VirtualServer extends AutoCloseable {
    boolean isConnected();

    void send(ClientMessage message) throws IOException;

    void sendRaw(String serializedMessage) throws IOException;

    void authenticate(String nickname) throws IOException;

    void handleError(ErrorPayload payload);

    void handlePreMatchUpdate(PreMatchUpdatePayload payload);

    void handleGameUpdate(GamePayload payload);

    @Override
    void close() throws IOException;
}
