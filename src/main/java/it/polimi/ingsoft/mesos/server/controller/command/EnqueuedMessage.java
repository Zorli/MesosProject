package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

import java.util.Objects;
//Solo e solamente messaggi dai client
public class EnqueuedMessage implements QueuedCommand {
    private final String sessionId;
    private final ClientPayload payload;

    public EnqueuedMessage(String sessionId, ClientPayload payload) {
        this.sessionId = sessionId;
        this.payload = payload;
    }

    @Override
    public String getSessionId() {
        return this.sessionId;
    }

    @Override
    public ClientPayload getPayload() {
        return this.payload;
    }

}
