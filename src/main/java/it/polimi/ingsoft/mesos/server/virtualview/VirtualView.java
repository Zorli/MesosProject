package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;

public interface VirtualView {
    void handleClientMessage(ClientMessage clientMessage);
    void pong(PongPayload payload);
    void closeSession();
}
