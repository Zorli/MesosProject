package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;

import java.util.Objects;

public class EnqueuedMessage {
    private final String sessionId;
    private final ClientPayload payload;

    public EnqueuedMessage(String sessionId, ClientPayload payload) {
        this.sessionId = sessionId;
        this.payload = payload;
    }

    public String sessionId() {
        return this.sessionId;
    }

    public ClientPayload payload() {
        return this.payload;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EnqueuedMessage that)) {
            return false;
        }
        return Objects.equals(this.sessionId, that.sessionId)
                && Objects.equals(this.payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.sessionId, this.payload);
    }

    @Override
    public String toString() {
        return "EnqueuedMessage[sessionId=" + this.sessionId + ", payload=" + this.payload + "]";
    }
}
