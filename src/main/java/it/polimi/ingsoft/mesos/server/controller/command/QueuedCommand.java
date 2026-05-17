package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

// Common type for controller queue entries, implemented by wrappers for network payloads and internal server commands
public interface QueuedCommand {
    public String getSessionId();
    public ClientPayload getPayload();
}
