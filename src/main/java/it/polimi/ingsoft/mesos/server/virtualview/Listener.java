package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;

public interface Listener {
    void notifyUpdate(ServerPayload payload);
}
