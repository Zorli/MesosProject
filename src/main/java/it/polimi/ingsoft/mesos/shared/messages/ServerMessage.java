package it.polimi.ingsoft.mesos.shared.messages;

import java.io.Serializable;
import java.util.Objects;

public class ServerMessage implements Serializable {

    public final ServerMessageType type;
    public final Object payload;

    public ServerMessage(ServerMessageType type, Object payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
