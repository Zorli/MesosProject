package it.polimi.ingsoft.mesos.server.messages;

import java.util.Objects;

public class ServerMessage {
    public final MessageType type;
    public final Object payload;

    public ServerMessage(MessageType type, Object payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
