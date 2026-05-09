package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

import java.util.Objects;

public class ClientQueuedCommand implements QueuedCommand {
    private final ClientPayload payload;

    public ClientQueuedCommand(ClientPayload payload) {
        this.payload = payload;
    }

    public ClientPayload payload() {
        return this.payload;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ClientQueuedCommand that)) {
            return false;
        }
        return Objects.equals(this.payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.payload);
    }
}
