package it.polimi.ingsoft.mesos.shared.messages;

import java.util.Objects;

public class ServerMessage {
    public final ServerMessageType type;
    public final Object payload;

    public ServerMessage(ServerMessageType type, Object payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
