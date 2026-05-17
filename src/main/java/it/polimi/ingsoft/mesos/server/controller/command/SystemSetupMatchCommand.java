package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

public class SystemSetupMatchCommand implements QueuedCommand {
    @Override
    public String getSessionId() {
        return "SYSTEM";
    }
    @Override
    public ClientPayload getPayload() {
        return null;
    }
}
