package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;

import java.io.IOException;

public interface VirtualServer extends AutoCloseable {
    boolean isConnected();

    void send(ClientMessage message) throws IOException;

    void sendRaw(String serializedMessage) throws IOException;

    void authenticate(String nickname) throws IOException;

    @Override
    void close() throws IOException;
}
