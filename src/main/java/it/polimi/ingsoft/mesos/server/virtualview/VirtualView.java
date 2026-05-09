package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.PongPayload;

public interface VirtualView {
    void sendMessage(ClientPayload payload);
    void pong(PongPayload payload);
    void closeSession();
}
