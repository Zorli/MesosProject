package it.polimi.ingsoft.mesos.shared.messages;

import java.util.Objects;

public class ClientMessage {
    public final ClientMessageType type;
    public final Object payload;

    public ClientMessage(ClientMessageType type, Object payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
