package it.polimi.ingsoft.mesos.server.network;

import java.io.Closeable;

public interface PingPongConnection extends Closeable {
    void sendPing();
}
