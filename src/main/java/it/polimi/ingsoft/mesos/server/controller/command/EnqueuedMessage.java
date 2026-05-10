package it.polimi.ingsoft.mesos.server.controller.command;

import java.util.Objects;

public class EnqueuedMessage {
    private final String sessionId;
    private final QueuedCommand command;

    public EnqueuedMessage(String sessionId, QueuedCommand command) {
        this.sessionId = sessionId;
        this.command = command;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public QueuedCommand getCommand() {
        return this.command;
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
                && Objects.equals(this.command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.sessionId, this.command);
    }

    @Override
    public String toString() {
        return "EnqueuedMessage[sessionId=" + this.sessionId + ", command=" + this.command + "]";
    }
}
