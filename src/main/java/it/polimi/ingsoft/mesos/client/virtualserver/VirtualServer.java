package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;

import java.io.IOException;

public interface VirtualServer extends AutoCloseable {
    void handleMessage(ServerMessage message);
    void handlePing();
    void close() throws IOException;
}
