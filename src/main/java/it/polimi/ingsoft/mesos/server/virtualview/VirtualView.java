package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.payload.client.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;

public interface VirtualView {
    void sendMessage(ClientPayload payload);
    void pong(PongPayload payload);
    void closeSession();
}
