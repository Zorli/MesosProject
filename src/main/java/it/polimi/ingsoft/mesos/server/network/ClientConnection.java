package it.polimi.ingsoft.mesos.server.network;

import java.io.Closeable;

public interface ClientConnection extends Closeable {
    void sendPing();
}
